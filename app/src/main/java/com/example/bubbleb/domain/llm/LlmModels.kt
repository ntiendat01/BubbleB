package com.example.bubbleb.domain.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class LlmRequest(val instruction: String, val untrustedText: String, val stream: Boolean)
data class TokenUsage(val input: Int?, val output: Int?)
enum class AiError {
    CONFIG_CHANGED, KEY_REQUIRED, AUTH, RATE_LIMIT, SERVER, HTTP, TIMEOUT, NETWORK,
    INVALID_RESPONSE, PARTIAL_STREAM, OUTPUT_LIMIT, STREAM_UNSUPPORTED, STORAGE,
}
sealed interface LlmEvent {
    data class Text(val delta: String) : LlmEvent
    data class Complete(val usage: TokenUsage?) : LlmEvent
    data class Failed(val reason: AiError) : LlmEvent
    data object JsonFallback : LlmEvent
}
fun interface LlmGateway {
    fun generate(credentials: ProviderCredentials, request: LlmRequest): Flow<LlmEvent>
}
object OfflineGateway : LlmGateway {
    override fun generate(credentials: ProviderCredentials, request: LlmRequest): Flow<LlmEvent> =
        flowOf(LlmEvent.Failed(AiError.KEY_REQUIRED))
}

data class UsageTotals(val requests: Int = 0, val inputTokens: Long = 0, val outputTokens: Long = 0)

/** No content, packages, request identifiers or disk history. */
class UsageCounter {
    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(UsageTotals())
    val totals: kotlinx.coroutines.flow.StateFlow<UsageTotals> = mutable
    @Synchronized fun started() { mutable.value = mutable.value.copy(requests = mutable.value.requests + 1) }
    @Synchronized fun completed(usage: TokenUsage?) {
        mutable.value = mutable.value.copy(
            inputTokens = mutable.value.inputTokens + (usage?.input ?: 0),
            outputTokens = mutable.value.outputTokens + (usage?.output ?: 0),
        )
    }
}
