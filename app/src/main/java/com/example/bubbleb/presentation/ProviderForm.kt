package com.example.bubbleb.presentation

import com.example.bubbleb.domain.llm.ProviderConfig
import com.example.bubbleb.domain.llm.LlmLimits
import com.example.bubbleb.domain.llm.ProviderPreset

data class ProviderForm(
    val name: String = ProviderPreset.NAME,
    val baseUrl: String = ProviderPreset.BASE_URL,
    val model: String = ProviderPreset.MODEL,
    val key: String = "",
    val tokens: String = LlmLimits.DEFAULT_OUTPUT_TOKENS.toString(),
    val inputLimit: String = LlmLimits.MAX_INPUT.toString(),
    val streaming: Boolean = true,
    val inputPrice: String = "",
    val outputPrice: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val notice: ProviderNotice? = null,
) {
    override fun toString() = "ProviderForm(<redacted>)"

    fun config(): ProviderConfig? {
        val maxTokens = tokens.toIntOrNull() ?: return null
        val limit = inputLimit.toIntOrNull() ?: return null
        val priceIn = inputPrice.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val priceOut = outputPrice.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        if ((inputPrice.isNotBlank() && priceIn == null) || (outputPrice.isNotBlank() && priceOut == null)) return null
        return ProviderConfig(name.trim(), baseUrl.trim().trimEnd('/'), model.trim(), maxTokens, limit, streaming, priceIn, priceOut)
            .takeIf { it.fieldsValid() }
    }

    companion object {
        fun from(config: ProviderConfig?) = config?.let {
            ProviderForm(it.name, it.baseUrl, it.model, tokens = it.maxOutputTokens.toString(), inputLimit = it.inputLimit.toString(),
                streaming = it.streaming, inputPrice = it.inputUsdPerMillion?.toString().orEmpty(),
                outputPrice = it.outputUsdPerMillion?.toString().orEmpty(), loading = false)
        } ?: ProviderForm(loading = false)
    }
}

enum class ProviderNotice { SAVED, REMOVED, INVALID_CONFIG, KEY_REQUIRED, STORAGE, TEST_OK, TEST_FAILED }
