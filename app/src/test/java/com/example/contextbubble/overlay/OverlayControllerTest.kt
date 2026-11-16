package com.example.contextbubble.overlay

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.contextbubble.domain.model.BubbleEdge
import com.example.contextbubble.domain.model.BubblePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * JVM (Robolectric) check of overlay attach/detach and gesture plumbing. This is NOT a device test:
 * Robolectric does not enforce TYPE_ACCESSIBILITY_OVERLAY tokens or OEM window behaviour.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OverlayControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var taps = 0
    private val committed = mutableListOf<BubblePosition>()
    private lateinit var controller: OverlayController

    @Before
    fun setUp() {
        controller = OverlayController(context, windowManager, onTap = { taps++ }, onPositionCommitted = { committed += it })
    }

    private fun attachedViews(): List<View> = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views

    @Test
    fun twentyAttachDetachCycles_leaveNoWindowsAndDestroyLifecycle() {
        repeat(20) {
            controller.show(BubblePosition(BubbleEdge.RIGHT, 0.5f))
            ShadowLooper.idleMainLooper()
            assertTrue(controller.isAttached)
            assertEquals(1, attachedViews().size)
            val owner = attachedViews().single().findViewTreeLifecycleOwner()
            assertNotNull(owner)
            assertEquals(Lifecycle.State.RESUMED, owner!!.lifecycle.currentState)
            controller.hide()
            assertFalse(controller.isAttached)
            assertEquals(0, attachedViews().size)
            assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        }
    }

    @Test
    fun repeatedShow_doesNotDuplicateWindow() {
        repeat(10) { controller.show(BubblePosition(BubbleEdge.LEFT, it / 10f)) }
        assertEquals(1, attachedViews().size)
        controller.release()
        controller.release() // idempotent
        controller.hide()
        assertEquals(0, attachedViews().size)
    }

    @Test
    fun windowParams_useAccessibilityOverlayAndNonFocusable() {
        controller.show(BubblePosition(BubbleEdge.LEFT, 0f))
        val params = attachedViews().single().layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        controller.release()
    }

    @Test
    fun tap_invokesOnTapOnly() {
        controller.show(BubblePosition(BubbleEdge.RIGHT, 0.5f))
        val view = attachedViews().single()
        touch(view, MotionEvent.ACTION_DOWN, 500f, 500f)
        touch(view, MotionEvent.ACTION_UP, 501f, 501f)
        assertEquals(1, taps)
        assertTrue(committed.isEmpty())
        controller.release()
    }

    @Test
    fun dragToLeft_snapsLeftAndCommitsOnce() {
        controller.show(BubblePosition(BubbleEdge.RIGHT, 0.5f))
        val view = attachedViews().single()
        touch(view, MotionEvent.ACTION_DOWN, 900f, 800f)
        touch(view, MotionEvent.ACTION_MOVE, 500f, 700f)
        touch(view, MotionEvent.ACTION_MOVE, 50f, 600f)
        touch(view, MotionEvent.ACTION_UP, 40f, 600f)
        assertEquals(0, taps)
        assertEquals(1, committed.size)
        assertEquals(BubbleEdge.LEFT, committed.single().edge)
        controller.release()
    }

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}
