package com.example.bubbleb.data.llm

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.UnknownHostException

object ProviderUrl {
    fun normalize(value: String): String? {
        if (value.length > 2_048 || value.any { it.isISOControl() } || value.contains('\\')) return null
        val url = value.toHttpUrlOrNull() ?: return null
        if (value.substringAfter("://").substringBefore('/').contains('@')) return null
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || url.host == "localhost" ||
            listOf(".localhost", ".local", ".internal").any(url.host::endsWith)) return null
        if (url.host.contains(':') || url.host.matches(Regex("[0-9.]+"))) {
            val address = try {
                if (url.host.contains(':')) InetAddress.getByName(url.host) // HttpUrl already validated this IPv6 literal.
                else {
                    val parts = url.host.split('.').map { it.toIntOrNull() }
                    if (parts.size != 4 || parts.any { it == null || it !in 0..255 }) return null
                    InetAddress.getByAddress(parts.map { it!!.toByte() }.toByteArray())
                }
            } catch (_: Exception) { return null }
            if (!isPublicAddress(address)) return null
        }
        return url.toString().trimEnd('/')
    }

    fun chatCompletions(baseUrl: String): HttpUrl {
        val normalized = normalize(baseUrl) ?: throw IllegalArgumentException("INVALID_PROVIDER_URL")
        val url = normalized.toHttpUrlOrNull()!!
        return if (url.encodedPath.endsWith("/chat/completions")) url
        else url.newBuilder().addPathSegment("chat").addPathSegment("completions").build()
    }

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        val first = bytes[0].toInt() and 255
        if (bytes.size == 16) return first and 254 != 252 // IPv6 unique-local.
        val second = bytes[1].toInt() and 255
        return first != 0 && first < 224 && !(first == 100 && second in 64..127)
    }
}

/** Rechecked at resolution time to reject private addresses behind apparently public hostnames. */
class PublicProviderDns(private val upstream: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = upstream.lookup(hostname).also { addresses ->
        if (addresses.isEmpty() || addresses.any { !ProviderUrl.isPublicAddress(it) }) {
            throw UnknownHostException("PROVIDER_ADDRESS_BLOCKED")
        }
    }
}
