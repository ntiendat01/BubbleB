package com.example.bubbleb.llm

import com.example.bubbleb.data.llm.ProviderUrl
import com.example.bubbleb.data.llm.PublicProviderDns
import com.example.bubbleb.di.LlmModule
import com.example.bubbleb.domain.llm.ProviderPreset
import com.example.bubbleb.domain.llm.LlmLimits
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test

class ProviderUrlTest {
    @Test fun baseOrFullPathAlwaysProducesOneChatCompletionsSuffix() {
        listOf(ProviderPreset.BASE_URL, ProviderPreset.BASE_URL + "/", ProviderPreset.BASE_URL + "/chat/completions").forEach {
            assertEquals(ProviderPreset.BASE_URL + "/chat/completions", ProviderUrl.chatCompletions(it).toString())
        }
        assertEquals("https://fixture.example/custom/chat/completions", ProviderUrl.chatCompletions("https://fixture.example/custom").toString())
    }

    @Test fun insecureCredentialsQueryFragmentsAndPrivateHostsAreRejected() {
        listOf(
            "http" + "://fixture.example/v1", "ftp://fixture.example", "https://user:pass@fixture.example/v1",
            "https://fixture.example/v1?key=anything", "https://fixture.example/v1#fragment",
            "https://localhost/v1", "https://host.local/v1", "https://127.0.0.1/v1", "https://10.0.0.1/v1",
            "https://192.168.1.1/v1", "https://[::1]/v1", "https://[fd00::1]/v1", "https://100.64.0.1/v1",
            "https://fixture.example/\n",
        ).forEach { assertNull(ProviderUrl.normalize(it)) }
    }

    @Test fun dnsAddressesAreCheckedIncludingIpv6AndMixedRanges() {
        listOf("127.0.0.1", "0.0.0.0", "192.168.0.2", "169.254.1.2", "::1", "fc00::1", "ff02::1").forEach {
            assertFalse(ProviderUrl.isPublicAddress(InetAddress.getByName(it)))
        }
        assertTrue(ProviderUrl.isPublicAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(ProviderUrl.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test fun publicHostnameResolvingToAnyPrivateAddressFailsClosed() {
        val public = InetAddress.getByName("1.1.1.1")
        val private = InetAddress.getByName("192.168.1.2")
        val dns = PublicProviderDns(Dns { listOf(public, private) })
        try { dns.lookup("fixture.example"); fail("Expected private route rejection") }
        catch (_: UnknownHostException) { }
        assertEquals(listOf(public), PublicProviderDns(Dns { listOf(public) }).lookup("fixture.example"))
    }

    @Test fun productionClientDisablesReplayRedirectsAndPersistentCache() {
        val client = LlmModule.client()
        assertFalse(client.retryOnConnectionFailure)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertTrue(client.dns is PublicProviderDns)
        assertNull(client.cache)
        assertTrue(client.interceptors.isEmpty())
        assertEquals(LlmLimits.CALL_TIMEOUT_SECONDS * 1_000, client.callTimeoutMillis.toLong())
    }
}
