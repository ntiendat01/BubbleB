package com.example.bubbleb.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.presentation.PanelState
import com.example.bubbleb.presentation.ReplyOperation
import com.example.bubbleb.ui.overlay.PanelCallbacks
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowWindowManagerImpl

/** Robolectric (JVM) check of the panel window; NOT a device test. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PanelOverlayControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val state = MutableStateFlow<PanelState>(PanelState.Capturing)
    private val callbacks = object : PanelCallbacks {
        override fun onClose() = Unit
        override fun onAction(action: AiAction) = Unit
        override fun onPreviewCancel() = Unit
        override fun onReplyAction(operation: ReplyOperation, index: Int) = Unit
        override fun onReplyConfirm() = Unit
        override fun onReplyCancel() = Unit
    }
    private val controller = PanelOverlayController(context, windowManager, state, callbacks)

    private fun views(): List<View> = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views

    @Test fun showHideCycles_noLeakedWindows() {
        repeat(10) {
            controller.show()
            controller.show() // idempotent
            ShadowLooper.idleMainLooper()
            assertEquals(1, views().size)
            controller.hide()
            assertEquals(0, views().size)
        }
    }

    @Test fun panelWindowIsNonFocusableAccessibilityOverlay() {
        controller.show()
        val params = views().single().layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertEquals("BubbleB Panel", params.title.toString())
        controller.hide()
    }
}
