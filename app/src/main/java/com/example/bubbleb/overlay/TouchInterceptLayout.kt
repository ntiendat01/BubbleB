package com.example.bubbleb.overlay

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Container that takes every touch away from its Compose child so the parent's
 * `OnTouchListener` (raw-coordinate drag + tap detection) always receives the gesture.
 *
 * Why: Material3 `Surface` installs an empty `pointerInput` that consumes touches. A listener
 * set directly on the `ComposeView` therefore never fired on a real, laid-out window — the bubble
 * ignored taps and drags (M2 device report). TalkBack still works through Compose semantics.
 */
class TouchInterceptLayout(context: Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true
}
