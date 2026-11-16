package com.example.bubbleb.overlay

import com.example.bubbleb.domain.model.IntPoint
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Result of a completed gesture on the bubble. */
sealed interface DragResult {
    data object Tap : DragResult
    data class DragEnd(val windowPosition: IntPoint) : DragResult
    data object None : DragResult
}

/**
 * Pure touch-slop/drag state machine using raw screen coordinates, so moving the window during
 * the drag does not feed back into the deltas.
 */
class BubbleDragTracker(private val touchSlopPx: Float) {
    private var downRawX = 0f
    private var downRawY = 0f
    private var startWindow = IntPoint(0, 0)
    private var active = false
    private var dragging = false

    fun onDown(rawX: Float, rawY: Float, windowPosition: IntPoint) {
        downRawX = rawX
        downRawY = rawY
        startWindow = windowPosition
        active = true
        dragging = false
    }

    /** Returns the new window position while dragging, or null while still within slop. */
    fun onMove(rawX: Float, rawY: Float): IntPoint? {
        if (!active) return null
        val dx = rawX - downRawX
        val dy = rawY - downRawY
        if (!dragging && hypot(dx, dy) < touchSlopPx) return null
        dragging = true
        return IntPoint(startWindow.x + dx.roundToInt(), startWindow.y + dy.roundToInt())
    }

    fun onUp(rawX: Float, rawY: Float): DragResult {
        if (!active) return DragResult.None
        val moved = onMove(rawX, rawY)
        val result = if (dragging) DragResult.DragEnd(moved ?: startWindow) else DragResult.Tap
        reset()
        return result
    }

    fun onCancel() = reset()

    private fun reset() {
        active = false
        dragging = false
    }
}
