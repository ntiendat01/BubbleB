package com.example.bubbleb.data.llm

import com.example.bubbleb.domain.llm.*
import com.example.bubbleb.domain.text.TextLimits
import okhttp3.Response

class ChatResponseReader {
    fun read(response: Response, streaming: Boolean, emit: (LlmEvent) -> Unit) {
        if (!response.isSuccessful) { emit(LlmEvent.Failed(httpError(response.code, streaming))); return }
        val body = response.body
        val type = body.contentType()
        val sse = type?.type == "text" && type.subtype == "event-stream"
        if (!sse) {
            if (streaming) emit(LlmEvent.JsonFallback)
            val source = body.source()
            if (source.request(LlmLimits.MAX_BODY_BYTES + 1)) throw OutputLimitReached()
            val root = ChatPayload.objectFrom(source.readUtf8())
            val text = ChatPayload.content(root, stream = false) ?: throw InvalidChatPayload()
            if (TextLimits.codePointLength(text) > LlmLimits.MAX_OUTPUT_CODE_POINTS) throw OutputLimitReached()
            if (text.isBlank()) throw InvalidChatPayload()
            emit(LlmEvent.Text(text))
            emit(LlmEvent.Complete(ChatPayload.usage(root)))
            return
        }
        val decoder = SseDecoder()
        val source = body.source()
        var totalBytes = 0L
        var outputPoints = 0
        var usage: TokenUsage? = null
        while (!source.exhausted()) {
            val line = source.readUtf8LineStrict(LlmLimits.MAX_EVENT_BYTES)
            totalBytes += line.toByteArray(Charsets.UTF_8).size + 1
            if (totalBytes > LlmLimits.MAX_STREAM_BYTES) throw OutputLimitReached()
            val data = decoder.line(line) ?: continue
            if (data.trim() == "[DONE]") {
                if (outputPoints == 0) throw InvalidChatPayload()
                emit(LlmEvent.Complete(usage))
                return
            }
            val root = ChatPayload.objectFrom(data)
            usage = ChatPayload.usage(root) ?: usage
            val text = ChatPayload.content(root, stream = true) ?: continue
            outputPoints += TextLimits.codePointLength(text)
            if (outputPoints > LlmLimits.MAX_OUTPUT_CODE_POINTS) throw OutputLimitReached()
            if (text.isNotEmpty()) emit(LlmEvent.Text(text))
        }
        emit(LlmEvent.Failed(AiError.PARTIAL_STREAM))
    }

    private fun httpError(code: Int, stream: Boolean): AiError = when (code) {
        401, 403 -> AiError.AUTH
        429 -> AiError.RATE_LIMIT
        in 500..599 -> AiError.SERVER
        400, 406, 415, 422 -> if (stream) AiError.STREAM_UNSUPPORTED else AiError.HTTP
        else -> AiError.HTTP
    }
}
