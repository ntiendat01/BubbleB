package com.example.bubbleb.data.config

import com.example.bubbleb.BuildConfig

/** One OpenAI-compatible endpoint + key. The key is never exposed through [toString]. */
data class LlmEndpoint(val baseUrl: String, val apiKey: String) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.startsWith("https://")

    override fun toString(): String =
        "LlmEndpoint(baseUrl=$baseUrl, apiKey=${if (apiKey.isEmpty()) "<empty>" else "<redacted>"})"
}

/**
 * Debug-only provider defaults injected from the repo-root `.env` at build time (ADR-029).
 * Default route: one gateway (CoreWeave Serverless Inference) serving both DeepSeek and Qwen
 * models; optional direct DeepSeek/Qwen endpoints. Release builds always see empty values.
 * From M3, values entered in-app (Keystore-encrypted) MUST override these. No network here.
 */
data class ProviderDefaults(
    val gateway: LlmEndpoint,
    val deepseekModel: String,
    val qwenModel: String,
    /** "deepseek", "qwen" or "" (unset). */
    val defaultModelFamily: String,
    val directDeepseek: LlmEndpoint,
    val directQwen: LlmEndpoint,
) {
    /** Model id to use by default, or "" when nothing is configured. */
    val defaultModelId: String
        get() = when (defaultModelFamily) {
            QWEN -> qwenModel
            DEEPSEEK -> deepseekModel
            else -> deepseekModel.ifBlank { qwenModel }
        }

    companion object {
        const val DEEPSEEK = "deepseek"
        const val QWEN = "qwen"

        fun fromBuildConfig(): ProviderDefaults = ProviderDefaults(
            gateway = LlmEndpoint(BuildConfig.LLM_BASE_URL, BuildConfig.LLM_API_KEY),
            deepseekModel = BuildConfig.DEEPSEEK_MODEL,
            qwenModel = BuildConfig.QWEN_MODEL,
            defaultModelFamily = BuildConfig.DEFAULT_MODEL,
            directDeepseek = LlmEndpoint(BuildConfig.DEEPSEEK_BASE_URL, BuildConfig.DEEPSEEK_API_KEY),
            directQwen = LlmEndpoint(BuildConfig.QWEN_BASE_URL, BuildConfig.QWEN_API_KEY),
        )
    }
}
