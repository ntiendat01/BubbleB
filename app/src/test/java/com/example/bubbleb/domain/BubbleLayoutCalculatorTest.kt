package com.example.bubbleb.domain

import com.example.bubbleb.domain.layout.BubbleLayoutCalculator
import com.example.bubbleb.domain.model.BubbleEdge
import com.example.bubbleb.domain.model.BubblePosition
import com.example.bubbleb.domain.model.IntPoint
import com.example.bubbleb.domain.model.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleLayoutCalculatorTest {
    private val size = 100
    private val margin = 10
    private val calc = BubbleLayoutCalculator(size, margin)
    private val portrait = IntRect(0, 50, 1000, 2050) // status bar 50px, nav bar excluded

    @Test
    fun resolve_leftTop_isInsideMargins() {
        val p = calc.resolve(BubblePosition(BubbleEdge.LEFT, 0f), portrait)
        assertEquals(IntPoint(10, 60), p)
    }

    @Test
    fun resolve_rightBottom_staysAboveBottomInset() {
        val p = calc.resolve(BubblePosition(BubbleEdge.RIGHT, 1f), portrait)
        assertEquals(IntPoint(1000 - 10 - 100, 2050 - 10 - 100), p)
    }

    @Test
    fun resolve_outOfRangeFraction_isClamped() {
        val high = calc.resolve(BubblePosition(BubbleEdge.LEFT, 5f), portrait)
        val low = calc.resolve(BubblePosition(BubbleEdge.LEFT, -1f), portrait)
        assertEquals(1940, high.y)
        assertEquals(60, low.y)
    }

    @Test
    fun snap_releaseLeftOfCenter_snapsLeft() {
        val pos = calc.snap(IntPoint(300, 1000), portrait)
        assertEquals(BubbleEdge.LEFT, pos.edge)
    }

    @Test
    fun snap_releaseRightOfCenter_snapsRight() {
        val pos = calc.snap(IntPoint(600, 1000), portrait)
        assertEquals(BubbleEdge.RIGHT, pos.edge)
    }

    @Test
    fun snap_middleOfDrag_roundTripsVertically() {
        val pos = calc.snap(IntPoint(800, 1000), portrait)
        val resolved = calc.resolve(pos, portrait)
        assertEquals(1000, resolved.y)
    }

    @Test
    fun snap_releaseOutsideArea_fractionClamped() {
        val pos = calc.snap(IntPoint(-500, 99_999), portrait)
        assertEquals(BubbleEdge.LEFT, pos.edge)
        assertEquals(1f, pos.yFraction)
    }

    @Test
    fun rotation_keepsEdgeAndRelativeHeight_insideNewArea() {
        val pos = calc.snap(IntPoint(900, 1000), portrait)
        val landscape = IntRect(0, 50, 2050, 1000)
        val p = calc.resolve(pos, landscape)
        assertEquals(2050 - 10 - 100, p.x)
        assertTrue(p.y in 60..(1000 - 10 - 100))
    }

    @Test
    fun clamp_keepsDragInside() {
        assertEquals(IntPoint(10, 60), calc.clamp(IntPoint(-100, -100), portrait))
        assertEquals(IntPoint(890, 1940), calc.clamp(IntPoint(5000, 5000), portrait))
    }

    @Test
    fun tinyArea_neverProducesNegativeRange() {
        val tiny = IntRect(0, 0, 50, 50)
        val p = calc.resolve(BubblePosition(BubbleEdge.RIGHT, 1f), tiny)
        assertEquals(IntPoint(10, 10), p)
        assertEquals(0f, calc.snap(IntPoint(0, 0), tiny).yFraction)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSize_rejected() {
        BubbleLayoutCalculator(0, 0)
    }
}
