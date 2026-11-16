package com.example.bubbleb.reply

import com.example.bubbleb.data.reply.ReplyJsonParser
import com.example.bubbleb.domain.reply.ReplyLimits
import com.example.bubbleb.domain.reply.ReplyOutput
import com.example.bubbleb.domain.text.TextLimits
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic output shared by reply tests. Never contains user/provider content. */
fun replyJson(text: String = "Mình đồng ý, hẹn gặp bạn nhé."): String = JsonObject(mapOf(
    "replies" to JsonArray(ReplyLimits.LABELS.map { label ->
        JsonObject(mapOf("label" to JsonPrimitive(label), "text" to JsonPrimitive(text)))
    }),
)).toString()

class ReplyJsonParserTest {
    private val parser = ReplyJsonParser()

    @Test fun validJson_hasExactlyThreeLabeledOptions() {
        val result = parser.parse(replyJson()) as ReplyOutput.Valid
        assertEquals(ReplyLimits.LABELS, result.options.map { it.label })
        assertTrue(result.options.all { it.text.isNotBlank() })
    }

    @Test fun malformedSchema_isCopyOnly() {
        listOf(
            "plain text", "[]", "null", "{", "{\"replies\":[]}",
            replyJson().replace("Ngắn gọn", "Unknown"),
            replyJson().replace("\"text\":", "\"command\":"),
            replyJson().replace("\"replies\":", "\"execute\":true,\"replies\":"),
            replyJson().replace("\"text\":\"Mình đồng ý, hẹn gặp bạn nhé.\"", "\"text\":123"),
            "```json\n${replyJson()}\n```",
        ).forEach { assertTrue(parser.parse(it) is ReplyOutput.CopyOnly) }
    }

    @Test fun blankOrOverlongOptions_cannotBeInserted() {
        assertTrue(parser.parse(replyJson(" \t ")) is ReplyOutput.CopyOnly)
        assertTrue(parser.parse(replyJson("x".repeat(ReplyLimits.MAX_OPTION_CODE_POINTS + 1))) is ReplyOutput.CopyOnly)
    }

    @Test fun controlsAndBidiAreRemoved_withoutSplittingUnicode() {
        val output = parser.parse(replyJson("Xin\u0000 chào\u202E 👋")) as ReplyOutput.Valid
        assertEquals("Xin chào 👋", output.options[0].text)
        val emoji = "👋".repeat(ReplyLimits.MAX_OPTION_CODE_POINTS)
        assertTrue(parser.parse(replyJson(emoji)) is ReplyOutput.Valid)
    }

    @Test fun fallbackIsBoundedAndTextOnly() {
        val result = parser.parse("x".repeat(ReplyLimits.MAX_RESPONSE_CHARS + 1)) as ReplyOutput.CopyOnly
        assertEquals(ReplyLimits.MAX_FALLBACK_CODE_POINTS, TextLimits.codePointLength(result.text))
        val raw = parser.parse("<script>doNothing()</script>") as ReplyOutput.CopyOnly
        assertTrue(raw.text.contains("<script>")) // UI renders Text, never HTML or executable commands.
        assertFalse(raw.text.isEmpty())
    }

    @Test fun emptyOutputHasNoActionableOption() {
        assertEquals(ReplyOutput.Empty, parser.parse(" \u0000\t "))
    }

    @Test fun excessiveNestingFallsBackBeforeJsonParsing() {
        assertTrue(parser.parse("[".repeat(1_000) + "]".repeat(1_000)) is ReplyOutput.CopyOnly)
        assertTrue(parser.parse(replyJson("Brackets in text: [[[[[[[[[[[[ and escaped quote: \"")) is ReplyOutput.Valid)
    }
}
