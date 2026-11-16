package com.example.bubbleb.llm

import com.example.bubbleb.data.llm.OkHttpLlmGateway
import com.example.bubbleb.domain.llm.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import org.junit.Assert.*
import org.junit.Test

class OkHttpLlmGatewayTest {
    private val request = LlmRequest("Synthetic instruction", "Synthetic text only", false)

    @Test fun postUsesExactModelPathAndOnlyReviewedText() = runBlocking {
        TlsChatFixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body(completion()).setHeader("Content-Type", "application/json").build())
            val flow = fixture.gateway.generate(fixture.credentials, request)
            assertEquals(0, fixture.server.requestCount) // Creating a cold flow performs no HTTP.
            val events = withTimeout(5_000) { flow.toList() }
            val recorded = fixture.server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("POST", recorded.method)
            assertEquals("/v1/chat/completions", recorded.target)
            assertEquals("Bearer synthetic-key", recorded.headers["Authorization"])
            val payload = Json.parseToJsonElement(recorded.body!!.utf8()).jsonObject
            assertEquals(ProviderPreset.MODEL, payload["model"]!!.jsonPrimitive.content)
            assertFalse(payload.toString().contains("synthetic-key"))
            assertTrue(events.last() is LlmEvent.Complete)
            assertEquals(TokenUsage(12, 3), (events.last() as LlmEvent.Complete).usage)
            assertEquals(1, fixture.server.requestCount)
        }
    }

    @Test fun fragmentedUtf8SseMultipleDataLinesAndDone_areDecoded() = runBlocking {
        TlsChatFixture().use { fixture ->
            val body = ": heartbeat\r\ndata: {\"choices\":[\r\ndata: {\"delta\":{\"content\":\"Xin 👋\"}}]}\r\n\r\n" +
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":7}}\n\ndata: [DONE]\n\n"
            fixture.server.enqueue(MockResponse.Builder().chunkedBody(body, 1).setHeader("Content-Type", "text/event-stream").build())
            val events = withTimeout(5_000) { fixture.gateway.generate(fixture.credentials, request.copy(stream = true)).toList() }
            assertTrue("Unexpected terminal event: ${events.lastOrNull()}", events.lastOrNull() is LlmEvent.Complete)
            assertEquals("Xin 👋", events.filterIsInstance<LlmEvent.Text>().joinToString("") { it.delta })
            assertEquals(TokenUsage(7, null), (events.last() as LlmEvent.Complete).usage)
        }
    }

    @Test fun jsonReturnedForStreaming_usesSameResponseWithoutReplay() = runBlocking {
        TlsChatFixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body(completion()).setHeader("Content-Type", "application/json").build())
            val events = fixture.gateway.generate(fixture.credentials, request.copy(stream = true)).toList()
            assertTrue(events.first() is LlmEvent.JsonFallback)
            assertTrue(events.last() is LlmEvent.Complete)
            assertEquals(1, fixture.server.requestCount)
        }
    }

    @Test fun http2SseUsesPlainDataFramesAndCompletes() = runBlocking {
        TlsChatFixture(http1Only = false).use { fixture ->
            val body = "data: {\"choices\":[{\"delta\":{\"content\":\"HTTP2 👋\"}}]}\n\ndata: [DONE]\n\n"
            fixture.server.enqueue(MockResponse.Builder().body(body).setHeader("Content-Type", "text/event-stream").build())
            val events = fixture.gateway.generate(fixture.credentials, request.copy(stream = true)).toList()
            assertTrue(events.last() is LlmEvent.Complete)
            assertEquals("HTTP/2", fixture.server.takeRequest(1, TimeUnit.SECONDS)!!.version)
            assertEquals("HTTP2 👋", events.filterIsInstance<LlmEvent.Text>().single().delta)
        }
    }

    @Test fun httpErrorsAreSanitizedAndNeverRetried() = runBlocking {
        TlsChatFixture().use { fixture ->
            val cases = mapOf(401 to AiError.AUTH, 403 to AiError.AUTH, 429 to AiError.RATE_LIMIT, 503 to AiError.SERVER, 302 to AiError.HTTP)
            for ((code, error) in cases) {
                fixture.server.enqueue(MockResponse.Builder().code(code).setHeader("Location", "https://other.example/")
                    .body("PRIVATE_PROMPT_AND_CREDENTIAL_ECHO").build())
                val events = fixture.gateway.generate(fixture.credentials, request).toList()
                assertEquals(listOf(LlmEvent.Failed(error)), events)
                assertFalse(events.toString().contains("PRIVATE_PROMPT"))
            }
            assertEquals(cases.size, fixture.server.requestCount)
        }
    }

    @Test fun missingDoneIsPartialAndMalformedJsonIsGeneric() = runBlocking {
        TlsChatFixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("data: {\"choices\":[{\"delta\":{\"content\":\"part\"}}]}\n\n")
                .setHeader("Content-Type", "text/event-stream").build())
            val partial = fixture.gateway.generate(fixture.credentials, request.copy(stream = true)).toList()
            assertEquals(LlmEvent.Failed(AiError.PARTIAL_STREAM), partial.last())
            fixture.server.enqueue(MockResponse.Builder().body("{PRIVATE-INVALID-JSON").build())
            assertEquals(listOf(LlmEvent.Failed(AiError.INVALID_RESPONSE)), fixture.gateway.generate(fixture.credentials, request).toList())
        }
    }

    @Test fun dismissCancelsActualOkHttpCall() = runBlocking {
        TlsChatFixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body(completion()).bodyDelay(2, TimeUnit.SECONDS).build())
            val started = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.IO) {
                started.complete(Unit)
                fixture.gateway.generate(fixture.credentials, request).collect { }
            }
            started.await()
            assertNotNull(fixture.server.takeRequest(3, TimeUnit.SECONDS))
            withTimeout(1_500) { job.cancelAndJoin() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (fixture.client.dispatcher.runningCallsCount() != 0 && System.nanoTime() < deadline) kotlinx.coroutines.delay(10)
            assertEquals(0, fixture.client.dispatcher.runningCallsCount())
            assertEquals(1, fixture.server.requestCount)
        }
    }

    @Test fun timeoutAndStreamingRejection_doNotReplay() = runBlocking {
        TlsChatFixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body(completion()).bodyDelay(1, TimeUnit.SECONDS).build())
            val fastClient = fixture.client.newBuilder().readTimeout(150, TimeUnit.MILLISECONDS).build()
            val events = OkHttpLlmGateway(fastClient).generate(fixture.credentials, request).toList()
            assertEquals(LlmEvent.Failed(AiError.TIMEOUT), events.last())
            fixture.server.enqueue(MockResponse.Builder().code(400).body("SECRET_ERROR_ECHO").build())
            val rejection = fixture.gateway.generate(fixture.credentials, request.copy(stream = true)).toList()
            assertEquals(listOf(LlmEvent.Failed(AiError.STREAM_UNSUPPORTED)), rejection)
            assertEquals(2, fixture.server.requestCount)
        }
    }

    @Test fun oversizedResponseIsBoundedAndDoesNotExposeBody() = runBlocking {
        TlsChatFixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("x".repeat(LlmLimits.MAX_BODY_BYTES.toInt() + 1)).build())
            val events = fixture.gateway.generate(fixture.credentials, request).toList()
            assertEquals(listOf(LlmEvent.Failed(AiError.OUTPUT_LIMIT)), events)
        }
    }
}
