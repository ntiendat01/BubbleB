package com.example.bubbleb.domain.usecase

import com.example.bubbleb.domain.CaptureLimits
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.ElementRole
import com.example.bubbleb.domain.model.ScreenContext
import com.example.bubbleb.domain.model.ScreenType
import com.example.bubbleb.domain.privacy.Redactor
import com.example.bubbleb.domain.text.TextLimits

/** Exactly what would be sent (sanitized), shown to the user before any upload. RAM only. */
data class PayloadPreview(
    val action: AiAction,
    val appLabel: String,
    val packageName: String,
    val text: String,
    val codePoints: Int,
    val redactionCounts: Map<Redactor.Category, Int>,
    val truncated: Boolean,
    /** Too little non-redacted text left: sending is disabled; user may cancel or use Share. */
    val mostlyRedacted: Boolean,
) {
    val hasRedactions: Boolean get() = redactionCounts.values.any { it > 0 }
}

/**
 * Builds the sanitized payload preview from an ephemeral [ScreenContext]. The user's own draft
 * (INPUT elements) is deliberately excluded from the payload. Redaction happens before the cap so
 * a secret can never be half-kept by truncation.
 */
object PayloadPreviewBuilder {
    const val MIN_MEANINGFUL_CODE_POINTS = 20

    fun build(context: ScreenContext, action: AiAction, captureTruncated: Boolean): PayloadPreview {
        val raw = context.elements.filter { it.role != ElementRole.INPUT }.joinToString("\n") { it.text }
        val redacted = Redactor.redact(raw)
        val cap = CaptureLimits.payloadCapFor(action)
        val text = TextLimits.truncate(redacted.text, cap)
        val cut = TextLimits.codePointLength(redacted.text) > cap
        val meaningful = TextLimits.codePointLength(Redactor.stripTokens(text).filterNot(Char::isWhitespace))
        return PayloadPreview(
            action = action,
            appLabel = context.appLabel,
            packageName = context.packageName,
            text = text,
            codePoints = TextLimits.codePointLength(text),
            redactionCounts = redacted.counts,
            truncated = cut || captureTruncated,
            mostlyRedacted = meaningful < MIN_MEANINGFUL_CODE_POINTS,
        )
    }
}

/** UI-only heuristics. Never used to claim who said what. */
object ScreenHeuristics {
    private const val DOCUMENT_MIN_CODE_POINTS = 1_500

    fun isReplyEligible(context: ScreenContext): Boolean =
        context.focusedInput?.let { !it.isPassword } == true &&
            context.elements.count { it.role != ElementRole.INPUT } >= 1

    fun classify(context: ScreenContext): ScreenType {
        val textChars = context.elements.filter { it.role != ElementRole.INPUT }
            .sumOf { TextLimits.codePointLength(it.text) }
        return when {
            context.focusedInput != null && context.elements.size >= 3 -> ScreenType.CHAT_LIKE
            context.focusedInput == null && textChars >= DOCUMENT_MIN_CODE_POINTS -> ScreenType.DOCUMENT_LIKE
            else -> ScreenType.UNKNOWN
        }
    }

    fun visibleCodePoints(context: ScreenContext): Int =
        context.elements.filter { it.role != ElementRole.INPUT }.sumOf { TextLimits.codePointLength(it.text) }
}
