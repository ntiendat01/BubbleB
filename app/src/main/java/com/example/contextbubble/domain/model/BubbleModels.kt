package com.example.contextbubble.domain.model

/** Immutable platform-free rectangle (maps android.graphics.Rect). Right/bottom are exclusive. */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
}

data class IntPoint(val x: Int, val y: Int)

enum class BubbleEdge { LEFT, RIGHT }

/**
 * Persisted bubble position: snapped edge plus vertical fraction of the usable range, so the
 * position survives rotation and inset changes.
 */
data class BubblePosition(val edge: BubbleEdge, val yFraction: Float)

enum class HideReason {
    SERVICE_DISCONNECTED,
    CONSENT_REQUIRED,
    DISABLED_BY_USER,
    SCREEN_LOCKED,
    UNKNOWN_FOREGROUND,
    OWN_APP,
    SYSTEM_SURFACE,
    EXCLUDED_APP,
}

sealed interface BubbleVisibility {
    data object Visible : BubbleVisibility
    data class Hidden(val reason: HideReason) : BubbleVisibility
}
