package com.example.bubbleb.presentation

import com.example.bubbleb.domain.llm.LlmEvent
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.text.TextLimits

/** One ephemeral response accumulator. Incomplete Reply JSON never becomes insertable UI. */
internal class PanelResponseReducer(
    private val appLabel: String,
    private val action: AiAction,
    private val publish: (PanelState.Output) -> Unit,
    private val completedReply: (String) -> Unit,
) {
    private val text = StringBuilder()
    private var fallback = false

    fun accept(event: LlmEvent) {
        when (event) {
            is LlmEvent.Text -> {
                text.append(event.delta)
                if (action != AiAction.REPLY) publish(PanelState.Output(appLabel, action,
                    TextLimits.display(text.toString()), jsonFallback = fallback))
            }
            LlmEvent.JsonFallback -> fallback = true
            is LlmEvent.Complete -> {
                if (action == AiAction.REPLY) completedReply(text.toString())
                else publish(PanelState.Output(appLabel, action, TextLimits.display(text.toString()),
                    loading = false, jsonFallback = fallback, usage = event.usage))
            }
            is LlmEvent.Failed -> publish(PanelState.Output(appLabel, action,
                TextLimits.display(text.toString()), loading = false, error = event.reason, jsonFallback = fallback))
        }
    }
}
