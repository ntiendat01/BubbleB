package com.example.bubbleb.data.llm

import com.example.bubbleb.domain.llm.*
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Single POST, no body logging or replay. Closing the collector cancels the call and response. */
class OkHttpLlmGateway(private val client: OkHttpClient) : LlmGateway {
    @OptIn(DelicateCoroutinesApi::class)
    override fun generate(credentials: ProviderCredentials, request: LlmRequest): Flow<LlmEvent> = callbackFlow {
        val httpRequest = Request.Builder()
            .url(ProviderUrl.chatCompletions(credentials.config.baseUrl))
            .header("Authorization", "Bearer " + credentials.apiKey)
            .header("Accept", if (request.stream) "text/event-stream, application/json" else "application/json")
            .post(ChatPayload.request(credentials, request).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val call = client.newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) trySend(LlmEvent.Failed(if (e is InterruptedIOException) AiError.TIMEOUT else AiError.NETWORK))
                close()
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!isClosedForSend) ChatResponseReader().read(response, request.stream) { event ->
                            if (trySendBlocking(event).isFailure) throw CancellationException()
                        }
                    } catch (_: CancellationException) {
                        // Collector dismissal is not a provider error.
                    } catch (_: OutputLimitReached) {
                        trySend(LlmEvent.Failed(AiError.OUTPUT_LIMIT))
                    } catch (_: EOFException) {
                        trySend(LlmEvent.Failed(if (request.stream) AiError.PARTIAL_STREAM else AiError.INVALID_RESPONSE))
                    } catch (e: IOException) {
                        if (!call.isCanceled()) trySend(LlmEvent.Failed(when {
                            e is InterruptedIOException -> AiError.TIMEOUT
                            request.stream -> AiError.PARTIAL_STREAM
                            else -> AiError.NETWORK
                        }))
                    } catch (_: RuntimeException) {
                        trySend(LlmEvent.Failed(AiError.INVALID_RESPONSE))
                    } finally { close() }
                }
            }
        })
        // Cancel aborts the socket/read. The callback's use block closes the body on that same
        // reader thread; concurrently closing it here can race Okio's read timeout bookkeeping.
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)
}
