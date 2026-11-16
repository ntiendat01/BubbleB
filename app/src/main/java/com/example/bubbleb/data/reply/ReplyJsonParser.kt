package com.example.bubbleb.data.reply

import com.example.bubbleb.domain.reply.ReplyLimits
import com.example.bubbleb.domain.reply.ReplyOption
import com.example.bubbleb.domain.reply.ReplyOutput
import com.example.bubbleb.domain.reply.ReplyParser
import com.example.bubbleb.domain.text.TextLimits
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Strict schema, bounded parsing; provider output is never interpreted as an Android action. */
class ReplyJsonParser : ReplyParser {
    override fun parse(response: String): ReplyOutput {
        val bounded = response.take(ReplyLimits.MAX_RESPONSE_CHARS).let {
            if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it
        }
        val fallback = TextLimits.truncate(TextLimits.normalize(bounded), ReplyLimits.MAX_FALLBACK_CODE_POINTS)
        if (fallback.isBlank()) return ReplyOutput.Empty
        if (response.length > ReplyLimits.MAX_RESPONSE_CHARS || !hasBoundedDepth(response)) return ReplyOutput.CopyOnly(fallback)
        val root = try {
            Json.parseToJsonElement(response) as? JsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val array = root?.takeIf { it.keys == setOf("replies") }?.get("replies") as? JsonArray
        if (array?.size != ReplyLimits.LABELS.size) return ReplyOutput.CopyOnly(fallback)
        val options = array.mapIndexed { index, element ->
            val item = element as? JsonObject ?: return ReplyOutput.CopyOnly(fallback)
            if (item.keys != setOf("label", "text")) return ReplyOutput.CopyOnly(fallback)
            val label = (item["label"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return ReplyOutput.CopyOnly(fallback)
            val raw = (item["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return ReplyOutput.CopyOnly(fallback)
            val text = TextLimits.normalize(raw)
            if (label != ReplyLimits.LABELS[index] || text.isBlank() ||
                TextLimits.codePointLength(text) > ReplyLimits.MAX_OPTION_CODE_POINTS) return ReplyOutput.CopyOnly(fallback)
            ReplyOption(label, text)
        }
        return ReplyOutput.Valid(options)
    }

    private fun hasBoundedDepth(value: String): Boolean {
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in value) {
            if (escaped) { escaped = false; continue }
            if (quoted && char == '\\') { escaped = true; continue }
            if (char == '"') quoted = !quoted
            if (quoted) continue
            if (char == '{' || char == '[') depth++
            if (char == '}' || char == ']') depth--
            if (depth > ReplyLimits.MAX_JSON_DEPTH || depth < 0) return false
        }
        return depth == 0 && !quoted
    }
}
