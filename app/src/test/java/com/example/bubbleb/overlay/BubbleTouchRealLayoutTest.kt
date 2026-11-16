package com.example.bubbleb.overlay

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.ui.overlay.BubbleContent
import com.example.bubbleb.ui.theme.BubbleBTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Regression for the M2 device report "tap on bubble does nothing". The original M1 tests touched
 * points OUTSIDE the 48 px bubble (e.g. 500,500), so Compose never hit-tested and the bug stayed
 * hidden. Here touches land on the Compose content inside a laid-out window, as on a phone.
 * Still a JVM (Robolectric) test, not a device test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BubbleTouchRealLayoutTest {
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private fun compose() = ComposeView(activity).apply {
        setContent { BubbleBTheme { BubbleContent(onActivate = {}) } }
    }

    private fun host(root: View) {
        activity.setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ShadowLooper.idleMainLooper()
    }

    private fun tap(view: View) {
        val now = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val e = MotionEvent.obtain(now, now, action, view.width / 2f, view.height / 2f, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
        }
    }

    /** Documents the root cause: Surface's pointerInput swallows touches meant for the listener. */
    @Test fun listenerOnComposeViewItself_neverSeesTouches() {
        val view = compose()
        var events = 0
        view.setOnTouchListener { _, _ -> events++; true }
        host(view)
        assertTrue(view.width > 0)
        tap(view)
        assertEquals(0, events)
    }

    @Test fun touchInterceptLayout_deliversDownAndUpToListener() {
        val root = TouchInterceptLayout(activity).apply {
            addView(compose(), ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val actions = mutableListOf<Int>()
        root.setOnTouchListener { _, e -> actions += e.actionMasked; true }
        host(root)
        assertTrue(root.width > 0)
        tap(root)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), actions)
    }
}
