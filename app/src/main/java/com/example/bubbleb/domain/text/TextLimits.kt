package com.example.bubbleb.domain.text

/** Unicode-aware length/truncation helpers (code points, not UTF-16 units). */
object TextLimits {
    private val CONTROL_CHARS = Regex("[\\p{Cc}&&[^\\n\\t]]|[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val SPACES = Regex("[ \\t\\u00A0]+")

    fun codePointLength(text: String): Int = text.codePointCount(0, text.length)

    /** Returns at most [max] code points, never splitting a surrogate pair. */
    fun truncate(text: String, max: Int): String {
        if (max <= 0) return ""
        if (codePointLength(text) <= max) return text
        return text.substring(0, text.offsetByCodePoints(0, max))
    }

    /** Strips control/bidi-override characters and collapses horizontal whitespace. */
    fun normalize(text: String): String =
        text.replace(CONTROL_CHARS, "").replace(SPACES, " ").trim()

    /** Display generated text without collapsing translation formatting. */
    fun display(text: String): String = text.replace(CONTROL_CHARS, "").trim()
}
