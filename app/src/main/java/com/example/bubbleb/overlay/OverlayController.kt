package com.example.bubbleb.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.bubbleb.domain.BubbleDefaults
import com.example.bubbleb.domain.layout.BubbleLayoutCalculator
import com.example.bubbleb.domain.model.BubblePosition
import com.example.bubbleb.domain.model.IntPoint
import com.example.bubbleb.ui.overlay.BubbleContent
import com.example.bubbleb.ui.theme.BubbleBTheme
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
    private val density = context.resources.displayMetrics.density
    private val sizePx = (BubbleDefaults.SIZE_DP * density).roundToInt()
    private val calculator = BubbleLayoutCalculator(sizePx, (BubbleDefaults.EDGE_MARGIN_DP * density).roundToInt())
    private val safeArea = SafeAreaProvider(windowManager, density)
    private val dragTracker = BubbleDragTracker(ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var host: OverlayWindow? = null
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

    private fun attach(): OverlayWindow? {
        val params = OverlayWindows.params(sizePx, sizePx, WINDOW_TITLE)
        val window = OverlayWindows.attach(
            context = context,
            windowManager = windowManager,
            params = params,
            interceptTouches = true,
            configure = { w ->
                installTouchHandling(w)
                installInsetsListener(w)
            },
        ) { BubbleBTheme { BubbleContent(onActivate = onTap) } } ?: return null
        host = window
        return window
    }

    private fun detach() {
        val current = host ?: return
        host = null
        dragTracker.onCancel()
        OverlayWindows.detach(windowManager, current)
    }

    private fun place(target: OverlayWindow, pos: BubblePosition) {
        val point = calculator.resolve(pos, safeArea.currentSafeArea())
        move(target, point)
    }

    private fun move(target: OverlayWindow, point: IntPoint) {
        if (target.params.x == point.x && target.params.y == point.y) return
        target.params.x = point.x
        target.params.y = point.y
        OverlayWindows.update(windowManager, target)
    }

    @SuppressLint("ClickableViewAccessibility") // performClick() is invoked on tap below.
    private fun installTouchHandling(target: OverlayWindow) {
        target.root.setOnClickListener {
            Log.d(TAG, "bubble_tap")
            // Post so the tap handler (which detaches this window) never runs inside its own dispatch.
            mainHandler.post(onTap)
        }
        target.root.setOnTouchListener { v, event -> handleTouch(target, v, event) }
    }

    private fun handleTouch(target: OverlayWindow, view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                dragTracker.onDown(event.rawX, event.rawY, IntPoint(target.params.x, target.params.y))
            MotionEvent.ACTION_MOVE -> dragTracker.onMove(event.rawX, event.rawY)?.let { raw ->
                move(target, calculator.clamp(raw, safeArea.currentSafeArea()))
            }
            MotionEvent.ACTION_UP -> when (val result = dragTracker.onUp(event.rawX, event.rawY)) {
                DragResult.Tap -> view.performClick()
                is DragResult.DragEnd -> {
                    Log.d(TAG, "bubble_drag_end")
                    commitDrag(target, result.windowPosition)
                }
                DragResult.None -> Unit
            }
            MotionEvent.ACTION_CANCEL -> {
                dragTracker.onCancel()
                relayout()
            }
        }
        return true
    }

    private fun commitDrag(target: OverlayWindow, release: IntPoint) {
        val area = safeArea.currentSafeArea()
        val snapped = calculator.snap(calculator.clamp(release, area), area)
        position = snapped
        place(target, snapped)
        onPositionCommitted(snapped)
    }

    private fun installInsetsListener(target: OverlayWindow) {
        ViewCompat.setOnApplyWindowInsetsListener(target.root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (ime != safeArea.observedImeBottomPx) {
                safeArea.observedImeBottomPx = ime
                relayout()
            }
            insets
        }
    }

    private companion object {
        const val TAG = "BB/Overlay"
        const val WINDOW_TITLE = "BubbleB"
    }
}
