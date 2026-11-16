package com.example.bubbleb.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** One attached accessibility-overlay window: ComposeView + its own lifecycle owner + params. */
class OverlayWindow(
    val view: ComposeView,
    val owner: OverlayLifecycleOwner,
    val params: WindowManager.LayoutParams,
)

/**
 * Shared WindowManager plumbing for bubble and panel (ARCHITECTURE.md §4 OverlayController):
 * tree owners wired and RESUMED before `addView`, defensive add/update/remove, composition
 * disposed exactly once. Stage-only logging.
 */
object OverlayWindows {
    private const val TAG = "BB/Overlay"

    fun params(width: Int, height: Int, title: String) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.title = title
    }

    /** Creates the view, lets [configure] install listeners, then adds it. Null on failure. */
    fun attach(
        context: Context,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
        configure: (OverlayWindow) -> Unit = {},
        content: @Composable () -> Unit,
    ): OverlayWindow? {
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(context)
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent(content)
        val window = OverlayWindow(view, owner, params)
        configure(window)
        owner.createAndResume()
        return try {
            windowManager.addView(view, params)
            log("attached:${params.title}")
            window
        } catch (e: WindowManager.BadTokenException) {
            failAttach(window, e)
        } catch (e: IllegalStateException) {
            failAttach(window, e)
        } catch (e: SecurityException) {
            failAttach(window, e)
        }
    }

    fun update(windowManager: WindowManager, window: OverlayWindow) {
        try {
            windowManager.updateViewLayout(window.view, window.params)
        } catch (e: IllegalArgumentException) {
            log("update_not_attached")
        }
    }

    fun detach(windowManager: WindowManager, window: OverlayWindow) {
        try {
            windowManager.removeViewImmediate(window.view)
        } catch (e: IllegalArgumentException) {
            log("remove_not_attached")
        }
        window.view.disposeComposition()
        window.owner.destroy()
        log("detached:${window.params.title}")
    }

    private fun failAttach(window: OverlayWindow, error: Exception): OverlayWindow? {
        log("attach_failed:${error.javaClass.simpleName}")
        window.view.disposeComposition()
        window.owner.destroy()
        return null
    }

    private fun log(stage: String) {
        Log.i(TAG, stage)
    }
}
