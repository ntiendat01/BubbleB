package com.example.contextbubble.domain.layout

import com.example.contextbubble.domain.model.BubbleEdge
import com.example.contextbubble.domain.model.BubblePosition
import com.example.contextbubble.domain.model.IntPoint
import com.example.contextbubble.domain.model.IntRect
import kotlin.math.roundToInt

/**
 * Pure geometry for the bubble window: map a persisted [BubblePosition] into the current safe
 * area, clamp drags, and snap to the nearer horizontal edge on release.
 */
class BubbleLayoutCalculator(private val sizePx: Int, private val marginPx: Int) {

    init {
        require(sizePx > 0) { "sizePx must be positive" }
        require(marginPx >= 0) { "marginPx must not be negative" }
    }

    fun resolve(position: BubblePosition, safeArea: IntRect): IntPoint {
        val x = when (position.edge) {
            BubbleEdge.LEFT -> minX(safeArea)
            BubbleEdge.RIGHT -> maxX(safeArea)
        }
        val fraction = position.yFraction.coerceIn(0f, 1f)
        val y = minY(safeArea) + ((maxY(safeArea) - minY(safeArea)) * fraction).roundToInt()
        return IntPoint(x, y)
    }

    /** Keeps an in-progress drag fully inside the safe area. */
    fun clamp(point: IntPoint, safeArea: IntRect): IntPoint = IntPoint(
        x = point.x.coerceIn(minX(safeArea), maxX(safeArea)),
        y = point.y.coerceIn(minY(safeArea), maxY(safeArea)),
    )

    /** Converts a release point (window top-left) into a snapped, persistable position. */
    fun snap(release: IntPoint, safeArea: IntRect): BubblePosition {
        val centerX = release.x + sizePx / 2
        val edge = if (centerX < safeArea.centerX) BubbleEdge.LEFT else BubbleEdge.RIGHT
        val range = maxY(safeArea) - minY(safeArea)
        val fraction = if (range <= 0) 0f else (release.y - minY(safeArea)).toFloat() / range
        return BubblePosition(edge, fraction.coerceIn(0f, 1f))
    }

    private fun minX(area: IntRect) = area.left + marginPx
    private fun maxX(area: IntRect) = maxOf(minX(area), area.right - marginPx - sizePx)
    private fun minY(area: IntRect) = area.top + marginPx
    private fun maxY(area: IntRect) = maxOf(minY(area), area.bottom - marginPx - sizePx)
}
