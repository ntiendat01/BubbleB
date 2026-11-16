package com.example.bubbleb.overlay

import com.example.bubbleb.domain.model.IntPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BubbleDragTrackerTest {
    private val tracker = BubbleDragTracker(touchSlopPx = 10f)

    @Test
    fun smallMovement_isTap() {
        tracker.onDown(100f, 100f, IntPoint(0, 0))
        assertNull(tracker.onMove(104f, 103f))
        assertEquals(DragResult.Tap, tracker.onUp(105f, 104f))
    }

    @Test
    fun movementBeyondSlop_dragsUsingRawDelta() {
        tracker.onDown(100f, 100f, IntPoint(20, 30))
        assertEquals(IntPoint(70, 30), tracker.onMove(150f, 100f))
        assertEquals(IntPoint(70, 230), tracker.onMove(150f, 300f))
        assertEquals(DragResult.DragEnd(IntPoint(80, 240)), tracker.onUp(160f, 310f))
    }

    @Test
    fun onceDragging_returningWithinSlop_staysDrag() {
        tracker.onDown(0f, 0f, IntPoint(0, 0))
        tracker.onMove(50f, 0f)
        assertEquals(DragResult.DragEnd(IntPoint(1, 0)), tracker.onUp(1f, 0f))
    }

    @Test
    fun cancel_resetsState() {
        tracker.onDown(0f, 0f, IntPoint(0, 0))
        tracker.onMove(50f, 50f)
        tracker.onCancel()
        assertEquals(DragResult.None, tracker.onUp(60f, 60f))
        assertNull(tracker.onMove(100f, 100f))
    }

    @Test
    fun upWithoutDown_isNone() {
        assertEquals(DragResult.None, tracker.onUp(0f, 0f))
    }
}
