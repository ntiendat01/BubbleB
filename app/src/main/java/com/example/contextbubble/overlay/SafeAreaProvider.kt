package com.example.contextbubble.overlay

import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.example.contextbubble.domain.BubbleDefaults
import com.example.contextbubble.domain.model.IntRect
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Computes the usable screen area (in screen coordinates) for the bubble: display bounds minus
 * system bars, display cutout and — when reported — the IME. Best-effort; OEM behaviour must be
 * verified on the device (DEVICE_VERIFICATION_PENDING).
 */
class SafeAreaProvider(
    private val windowManager: WindowManager,
    private val density: Float,
) {
    /** Extra IME inset observed from the overlay view's own insets (0 when unknown). */
    var observedImeBottomPx: Int = 0

    fun currentSafeArea(): IntRect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) modernSafeArea() else legacySafeArea()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun modernSafeArea(): IntRect {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets
        val bars = insets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
        val bottomInset = max(bars.bottom, max(ime, observedImeBottomPx))
        return IntRect(
            left = bounds.left + bars.left,
            top = bounds.top + bars.top,
            right = bounds.right - bars.right,
            bottom = bounds.bottom - bottomInset,
        )
    }

    @Suppress("DEPRECATION")
    private fun legacySafeArea(): IntRect {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        val top = (BubbleDefaults.LEGACY_TOP_INSET_DP * density).roundToInt()
        val bottom = max((BubbleDefaults.LEGACY_BOTTOM_INSET_DP * density).roundToInt(), observedImeBottomPx)
        return IntRect(0, top, dm.widthPixels, dm.heightPixels - bottom)
    }
}
