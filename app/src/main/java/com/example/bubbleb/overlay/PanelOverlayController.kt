package com.example.bubbleb.overlay

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.example.bubbleb.domain.model.IntRect
import com.example.bubbleb.presentation.PanelState
import com.example.bubbleb.ui.overlay.PanelCallbacks
import com.example.bubbleb.ui.overlay.PanelContent
import com.example.bubbleb.ui.theme.BubbleBTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

/**
 * Separate non-focusable overlay window for the action/review panel. Width ≈ 90% of the safe
 * area, height bounded to [MAX_HEIGHT_FRACTION] of it and scrollable. Shown only while the panel
 * state is not Collapsed; fully detached otherwise.
 */
class PanelOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val state: StateFlow<PanelState>,
    private val callbacks: PanelCallbacks,
) {
    private val density = context.resources.displayMetrics.density
    private val safeArea = SafeAreaProvider(windowManager, density)
    private var window: OverlayWindow? = null

    val isAttached: Boolean get() = window != null

    /** Returns false when the panel window could not be added (caller must show feedback). */
    fun show(): Boolean {
        val area = safeArea.currentSafeArea()
        val width = (area.width * WIDTH_FRACTION).roundToInt()
        val maxHeightPx = (area.height * MAX_HEIGHT_FRACTION).roundToInt()
        val current = window
        if (current == null) {
            val params = OverlayWindows.params(width, WindowManager.LayoutParams.WRAP_CONTENT, WINDOW_TITLE)
            position(params, area, width, maxHeightPx)
            val maxHeightDp = (maxHeightPx / density).dp
            window = OverlayWindows.attach(context, windowManager, params) {
                val panelState by state.collectAsState()
                BubbleBTheme { PanelContent(panelState, appLabelFallback = "", maxHeight = maxHeightDp, callbacks) }
            }
            return window != null
        }
        current.params.width = width
        position(current.params, area, width, maxHeightPx)
        OverlayWindows.update(windowManager, current)
        return true
    }

    fun hide() {
        val current = window ?: return
        window = null
        OverlayWindows.detach(windowManager, current)
    }

    private fun position(
        params: WindowManager.LayoutParams,
        area: IntRect,
        width: Int,
        maxHeightPx: Int,
    ) {
        params.x = area.left + (area.width - width) / 2
        params.y = area.top + (area.height - maxHeightPx) / 2
    }

    private companion object {
        const val WINDOW_TITLE = "BubbleB Panel"
        const val WIDTH_FRACTION = 0.9f
        const val MAX_HEIGHT_FRACTION = 0.7f
    }
}
