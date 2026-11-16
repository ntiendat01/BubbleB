package com.example.contextbubble.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.contextbubble.domain.BubbleDefaults
import com.example.contextbubble.domain.layout.BubbleLayoutCalculator
import com.example.contextbubble.domain.model.BubblePosition
import com.example.contextbubble.domain.model.IntPoint
import com.example.contextbubble.ui.overlay.BubbleContent
import com.example.contextbubble.ui.theme.ContextBubbleTheme
import kotlin.math.roundToInt

/**
 * Owns the bubble's WindowManager view (TYPE_ACCESSIBILITY_OVERLAY). Must be used on the main
 * thread from the accessibility service context. Hiding fully detaches the view (no invisible
 * touch-blocking window); every attach gets a fresh ComposeView and lifecycle owner.
 */
class OverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onTap: () -> Unit,
    private val onPositionCommitted: (BubblePosition) -> Unit,
) {
    private class Host(val view: ComposeView, val owner: OverlayLifecycleOwner, val params: WindowManager.LayoutParams)

    private val density = context.resources.displayMetrics.density
    private val sizePx = (BubbleDefaults.SIZE_DP * density).roundToInt()
    private val calculator = BubbleLayoutCalculator(sizePx, (BubbleDefaults.EDGE_MARGIN_DP * density).roundToInt())
    private val safeArea = SafeAreaProvider(windowManager, density)
    private val dragTracker = BubbleDragTracker(ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private var host: Host? = null
    private var position: BubblePosition? = null

    val isAttached: Boolean get() = host != null

    fun show(newPosition: BubblePosition) {
        position = newPosition
        val current = host ?: attach() ?: return
        place(current, newPosition)
    }

    fun hide() = detach()

    /** Re-applies the persisted position after rotation / inset / display changes. */
    fun relayout() {
        val current = host ?: return
        position?.let { place(current, it) }
    }

    fun release() {
        detach()
        position = null
    }

    private fun attach(): Host? {
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(context)
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent { ContextBubbleTheme { BubbleContent(onActivate = onTap) } }
        val params = layoutParams()
        val newHost = Host(view, owner, params)
        installTouchHandling(newHost)
        installInsetsListener(newHost)
        owner.createAndResume()
        return try {
            windowManager.addView(view, params)
            host = newHost
            log("overlay_attached")
            newHost
        } catch (e: WindowManager.BadTokenException) {
            failAttach(newHost, e)
        } catch (e: IllegalStateException) {
            failAttach(newHost, e)
        } catch (e: SecurityException) {
            failAttach(newHost, e)
        }
    }

    private fun failAttach(failed: Host, error: Exception): Host? {
        log("overlay_attach_failed:${error.javaClass.simpleName}")
        failed.view.disposeComposition()
        failed.owner.destroy()
        return null
    }

    private fun detach() {
        val current = host ?: return
        host = null
        dragTracker.onCancel()
        try {
            windowManager.removeViewImmediate(current.view)
        } catch (e: IllegalArgumentException) {
            log("overlay_remove_not_attached")
        }
        current.view.disposeComposition()
        current.owner.destroy()
        log("overlay_detached")
    }

    private fun place(target: Host, pos: BubblePosition) {
        val point = calculator.resolve(pos, safeArea.currentSafeArea())
        move(target, point)
    }

    private fun move(target: Host, point: IntPoint) {
        if (target.params.x == point.x && target.params.y == point.y) return
        target.params.x = point.x
        target.params.y = point.y
        try {
            windowManager.updateViewLayout(target.view, target.params)
        } catch (e: IllegalArgumentException) {
            log("overlay_update_not_attached")
        }
    }

    @SuppressLint("ClickableViewAccessibility") // performClick() is invoked on tap below.
    private fun installTouchHandling(target: Host) {
        target.view.setOnClickListener { onTap() }
        target.view.setOnTouchListener { v, event -> handleTouch(target, v, event) }
    }

    private fun handleTouch(target: Host, view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                dragTracker.onDown(event.rawX, event.rawY, IntPoint(target.params.x, target.params.y))
            MotionEvent.ACTION_MOVE -> dragTracker.onMove(event.rawX, event.rawY)?.let { raw ->
                move(target, calculator.clamp(raw, safeArea.currentSafeArea()))
            }
            MotionEvent.ACTION_UP -> when (val result = dragTracker.onUp(event.rawX, event.rawY)) {
                DragResult.Tap -> view.performClick()
                is DragResult.DragEnd -> commitDrag(target, result.windowPosition)
                DragResult.None -> Unit
            }
            MotionEvent.ACTION_CANCEL -> {
                dragTracker.onCancel()
                relayout()
            }
        }
        return true
    }

    private fun commitDrag(target: Host, release: IntPoint) {
        val area = safeArea.currentSafeArea()
        val snapped = calculator.snap(calculator.clamp(release, area), area)
        position = snapped
        place(target, snapped)
        onPositionCommitted(snapped)
    }

    private fun installInsetsListener(target: Host) {
        ViewCompat.setOnApplyWindowInsetsListener(target.view) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (ime != safeArea.observedImeBottomPx) {
                safeArea.observedImeBottomPx = ime
                relayout()
            }
            insets
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = WINDOW_TITLE
    }

    /** Stage-only logging: never logs package names, screen text or positions tied to content. */
    private fun log(stage: String) {
        Log.i(TAG, stage)
    }

    private companion object {
        const val TAG = "CB/Overlay"
        const val WINDOW_TITLE = "ContextBubble"
    }
}
