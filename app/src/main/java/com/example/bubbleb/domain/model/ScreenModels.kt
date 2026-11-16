package com.example.bubbleb.domain.model

/** Contracts from ARCHITECTURE.md §5. Ephemeral only: never persisted, never logged. */
enum class AiAction { REPLY, SUMMARIZE, EXPLAIN, TRANSLATE }

/** Heuristic, UI-only. Never claims full semantic understanding. */
enum class ScreenType { UNKNOWN, CHAT_LIKE, DOCUMENT_LIKE }

enum class ElementRole { TEXT, LABEL, INPUT, UNKNOWN }

data class ScreenElement(
    val text: String,
    val role: ElementRole,
    val bounds: IntRect,
    val order: Int,
    val isEditable: Boolean,
)

data class InputSnapshot(
    val text: String,
    val selectionStart: Int?,
    val selectionEnd: Int?,
    val isPassword: Boolean,
)

data class ScreenContext(
    val packageName: String,
    val appLabel: String,
    val elements: List<ScreenElement>,
    val focusedInput: InputSnapshot?,
    val capturedAtMs: Long,
)

/** Why a capture produced no usable context. All of these fail closed (no payload). */
enum class CaptureFailure {
    NO_ROOT,
    UNKNOWN_PACKAGE,
    OWN_APP,
    EXCLUDED_APP,
    SCREEN_LOCKED,
    SENSITIVE_SCREEN,
    NO_TEXT,
}

/** Counters describing how the bounded traversal ended (no content). */
data class CaptureStats(
    val visitedNodes: Int,
    val maxDepthReached: Int,
    val hitNodeCap: Boolean,
    val hitDepthCap: Boolean,
    val hitTimeBudget: Boolean,
    val truncatedText: Boolean,
)

sealed interface CaptureResult {
    data class Success(val context: ScreenContext, val stats: CaptureStats) : CaptureResult
    data class Failure(val reason: CaptureFailure) : CaptureResult
}
