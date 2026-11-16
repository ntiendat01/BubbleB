package com.example.bubbleb.data.llm

import com.example.bubbleb.domain.llm.LlmLimits

/** Decodes SSE logical lines, independent of network read/chunk boundaries. */
class SseDecoder {
    private val data = StringBuilder()
    private var bytes = 0L
    private var hasData = false

    fun line(value: String): String? {
        bytes += value.toByteArray(Charsets.UTF_8).size + 1
        if (bytes > LlmLimits.MAX_EVENT_BYTES) throw InvalidChatPayload()
        if (value.isEmpty()) {
            val result = data.toString().takeIf { hasData }
            data.setLength(0)
            bytes = 0
            hasData = false
            return result
        }
        if (value.startsWith("data:")) {
            if (hasData) data.append('\n')
            data.append(value.removePrefix("data:").removePrefix(" "))
            hasData = true
        }
        return null
    }
}
