package com.example.bubbleb.domain.llm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LlmLimits {
    const val MIN_INPUT = 100
    const val MAX_INPUT = 6_000
    const val MIN_TOKENS = 64
    const val MAX_TOKENS = 4_096
    const val DEFAULT_OUTPUT_TOKENS = 512
    const val MAX_KEY_CHARS = 4_096
    const val MAX_BODY_BYTES = 262_144L
    const val MAX_EVENT_BYTES = 32_768L
    const val MAX_STREAM_BYTES = 524_288L
    const val MAX_OUTPUT_CODE_POINTS = 12_000
    const val CALL_TIMEOUT_SECONDS = 60L
    const val READ_TIMEOUT_SECONDS = 20L
}

object ProviderPreset {
    const val NAME = "CoreWeave"
    const val BASE_URL = "https://api.inference.wandb.ai/v1"
    const val MODEL = "deepseek-ai/DeepSeek-V4.1-Flash-1017"
}

@kotlinx.serialization.Serializable
data class ProviderConfig(
    val name: String = ProviderPreset.NAME,
    val baseUrl: String = ProviderPreset.BASE_URL,
    val model: String = ProviderPreset.MODEL,
    val maxOutputTokens: Int = LlmLimits.DEFAULT_OUTPUT_TOKENS,
    val inputLimit: Int = LlmLimits.MAX_INPUT,
    val streaming: Boolean = true,
    val inputUsdPerMillion: Double? = null,
    val outputUsdPerMillion: Double? = null,
) {
    fun fieldsValid(): Boolean = name.isNotBlank() && name.length <= 80 &&
        model.isNotBlank() && model.length <= 160 && model.none { it.isISOControl() } &&
        maxOutputTokens in LlmLimits.MIN_TOKENS..LlmLimits.MAX_TOKENS &&
        inputLimit in LlmLimits.MIN_INPUT..LlmLimits.MAX_INPUT &&
        listOf(inputUsdPerMillion, outputUsdPerMillion).all { it == null || (it.isFinite() && it in 0.0..1_000_000.0) }
}

enum class KeyIssue { NONE, MISSING, INVALIDATED, UNAVAILABLE }
enum class ProviderSource { NONE, DEBUG, SAVED }
data class ProviderStatus(
    val config: ProviderConfig? = null,
    val ready: Boolean = false,
    val keyIssue: KeyIssue = KeyIssue.MISSING,
    val source: ProviderSource = ProviderSource.NONE,
    val revision: Long = 0,
)

class ProviderCredentials(val config: ProviderConfig, val apiKey: String, val revision: Long) {
    override fun toString(): String = "ProviderCredentials(<redacted>)"
}

enum class ProviderProblem { INVALID_CONFIG, KEY_REQUIRED, STORAGE_UNAVAILABLE }
class ProviderException(val problem: ProviderProblem) : RuntimeException(problem.name)

interface ProviderRepository {
    val status: StateFlow<ProviderStatus>
    suspend fun refresh()
    suspend fun resolve(): ProviderCredentials?
    suspend fun save(config: ProviderConfig, key: String?)
    suspend fun clear()
}

/** Safe default for pure controller tests; production injects the encrypted implementation. */
object UnconfiguredProvider : ProviderRepository {
    override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus())
    override suspend fun refresh() = Unit
    override suspend fun resolve(): ProviderCredentials? = null
    override suspend fun save(config: ProviderConfig, key: String?) = Unit
    override suspend fun clear() = Unit
}
