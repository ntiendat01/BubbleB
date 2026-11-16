package com.example.bubbleb.llm

import com.example.bubbleb.data.llm.ChatPayload
import com.example.bubbleb.data.llm.SseDecoder
import org.junit.Assert.*
import org.junit.Test

class SseDecoderTest {
    @Test fun multilineDataAndCommentsProduceOneJsonEvent() {
        val decoder = SseDecoder()
        assertNull(decoder.line(": heartbeat"))
        assertNull(decoder.line("data: {\"choices\":["))
        assertNull(decoder.line("data: {\"delta\":{\"content\":\"Xin 👋\"}}]}"))
        val event = decoder.line("")!!
        assertEquals("{\"choices\":[\n{\"delta\":{\"content\":\"Xin 👋\"}}]}", event)
        assertEquals("Xin 👋", ChatPayload.content(ChatPayload.objectFrom(event), true))
        assertNull(decoder.line("data: [DONE]"))
        assertEquals("[DONE]", decoder.line(""))
    }

    @Test fun emptyEventsDoNotCreateFakeData() {
        val decoder = SseDecoder()
        assertNull(decoder.line(""))
        assertNull(decoder.line(": keepalive"))
        assertNull(decoder.line(""))
    }
}
