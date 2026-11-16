package com.example.bubbleb.presentation

import com.example.bubbleb.domain.llm.*
import com.example.bubbleb.domain.model.AiAction
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Request transport orchestration. Opening a panel or preview never calls [run]. */
class PanelRequestRunner @Inject constructor(
    private val providers: ProviderRepository,
    private val gateway: LlmGateway,
    private val usage: UsageCounter,
) {
    suspend fun run(review: PanelState.PreviewConsent, allowed: suspend () -> Boolean, emit: (LlmEvent) -> Unit) {
        var terminal = false
        try {
            val credentials = providers.resolve()
            if (credentials == null) { emit(LlmEvent.Failed(AiError.KEY_REQUIRED)); return }
            if (review.provider.config != credentials.config || review.provider.revision != credentials.revision) {
                emit(LlmEvent.Failed(AiError.CONFIG_CHANGED)); return
            }
            if (!allowed()) return
            currentCoroutineContext().ensureActive()
            usage.started()
            val request = LlmRequest(
                ActionPrompt.instruction(review.action, review.language), review.preview.text,
                review.action != AiAction.REPLY && credentials.config.streaming,
            )
            gateway.generate(credentials, request).collect { event ->
                currentCoroutineContext().ensureActive()
                if (terminal) return@collect
                if (event is LlmEvent.Complete) { terminal = true; usage.completed(event.usage) }
                if (event is LlmEvent.Failed) terminal = true
                emit(event)
            }
            if (!terminal) emit(LlmEvent.Failed(AiError.PARTIAL_STREAM))
        } catch (e: CancellationException) { throw e }
        catch (_: ProviderException) { emit(LlmEvent.Failed(AiError.STORAGE)) }
        catch (_: Exception) { emit(LlmEvent.Failed(AiError.INVALID_RESPONSE)) }
    }
}
