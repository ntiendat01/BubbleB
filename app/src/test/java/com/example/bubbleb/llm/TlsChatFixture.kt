package com.example.bubbleb.llm

import com.example.bubbleb.data.llm.OkHttpLlmGateway
import com.example.bubbleb.domain.llm.*
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/** Local TLS server with a generated certificate; no production key, provider or screen data. */
class TlsChatFixture(http1Only: Boolean = true) : AutoCloseable {
    val server = MockWebServer()
    private val certificate = HeldCertificate.Builder().addSubjectAlternativeName("fixture.example").build()
    private val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
    private val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
    val client: OkHttpClient
    val credentials: ProviderCredentials
    val gateway: OkHttpLlmGateway

    init {
        server.useHttps(serverTls.sslSocketFactory())
        val protocols = if (http1Only) listOf(Protocol.HTTP_1_1) else listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        server.protocols = protocols
        server.start()
        client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .dns(Dns { listOf(InetAddress.getByName("127.0.0.1")) }).proxy(Proxy.NO_PROXY)
            .protocols(protocols)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .readTimeout(3, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
        credentials = ProviderCredentials(ProviderConfig(baseUrl = server.url("/v1").newBuilder().host("fixture.example").build().toString()),
            "synthetic" + "-key", 1)
        gateway = OkHttpLlmGateway(client)
    }

    override fun close() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.close()
    }
}

fun completion(text: String = "Synthetic OK") = """{"choices":[{"message":{"content":"$text"},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":3}}"""
