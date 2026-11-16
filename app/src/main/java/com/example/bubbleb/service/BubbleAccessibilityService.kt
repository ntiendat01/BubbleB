package com.example.bubbleb.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.BubbleVisibility
import com.example.bubbleb.domain.repository.ScreenCaptureSource
import com.example.bubbleb.overlay.OverlayController
import com.example.bubbleb.overlay.PanelOverlayController
import com.example.bubbleb.presentation.BubbleCoordinator
import com.example.bubbleb.presentation.PanelCoordinator
import com.example.bubbleb.presentation.PanelState
import com.example.bubbleb.ui.overlay.PanelCallbacks
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Android adapter only (AGENTS.md §2): forwards connection, foreground-package and lock signals to
 * [BubbleCoordinator], renders bubble and panel windows, and performs a screen snapshot ONLY when
 * the user taps the bubble (via [PanelCoordinator]). It never calls the network, never logs screen
 * content and never persists state decisions itself.
 */
@AndroidEntryPoint
class BubbleAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: BubbleCoordinator
    @Inject lateinit var panelCoordinator: PanelCoordinator
    @Inject lateinit var clock: Clock

    private var scope: CoroutineScope? = null
    private var overlay: OverlayController? = null
    private var lockMonitor: ScreenLockMonitor? = null
    private var panel: PanelOverlayController? = null
    private var captureSource: ScreenCaptureSource? = null
    private var lastOrientation: Int? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        teardown() // Defensive: the OS may rebind without a prior unbind callback.
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        val controller = OverlayController(
            context = this,
            windowManager = getSystemService(WindowManager::class.java),
            onTap = ::onBubbleTapped,
            onPositionCommitted = { position -> serviceScope.launch { coordinator.saveBubblePosition(position) } },
        )
        overlay = controller
        captureSource = AccessibilityScreenCapture(this, clock)
        val panelController = PanelOverlayController(
            context = this,
            windowManager = getSystemService(WindowManager::class.java),
            state = panelCoordinator.state,
            callbacks = panelCallbacks,
        )
        panel = panelController
        lastOrientation = resources.configuration.orientation
        val monitor = ScreenLockMonitor(this) { locked -> coordinator.onScreenLockChanged(locked) }
        lockMonitor = monitor
        monitor.start()

        coordinator.onServiceConnected()
        coordinator.onScreenLockChanged(monitor.isLockedNow())
        refreshForeground(eventPackage = null)

        serviceScope.launch {
            combine(coordinator.bubbleVisibility, coordinator.bubblePosition, panelCoordinator.state) { v, p, s ->
                Triple(v, p, s)
            }.collect { (visibility, position, panelState) ->
                // Bubble and panel are mutually exclusive; nothing is shown while hidden by policy.
                val bubbleVisible = visibility is BubbleVisibility.Visible
                if (bubbleVisible && panelState == PanelState.Collapsed) controller.show(position) else controller.hide()
                if (panelState != PanelState.Collapsed) panelController.show() else panelController.hide()
            }
        }
        serviceScope.launch {
            coordinator.isScreenLocked.collect { locked -> if (locked) panelCoordinator.dismiss() }
        }
        serviceScope.launch {
            // Policy hides the bubble (excluded app, lock, consent revoked...) => close any panel too.
            coordinator.bubbleVisibility.collect { v -> if (v !is BubbleVisibility.Visible) panelCoordinator.dismiss() }
        }
        Log.i(TAG, "service_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Minimal window metadata only: package name. Event text/content is never read.
        refreshForeground(eventPackage = event.packageName?.toString())
    }

    /**
     * Root package of the active window is authoritative; the event package is a fallback. Our own
     * non-focusable overlay must never be treated as the foreground app.
     */
    private fun refreshForeground(eventPackage: String?) {
        lockMonitor?.let { coordinator.onScreenLockChanged(it.isLockedNow()) }
        val rootPackage = readActiveRootPackage()
        if (rootPackage == null && eventPackage == packageName) return
        val foreground = rootPackage ?: eventPackage
        coordinator.onForegroundPackageChanged(foreground)
        panelCoordinator.onForegroundChanged(foreground)
    }

    private fun readActiveRootPackage(): String? {
        val root = try {
            rootInActiveWindow
        } catch (_: RuntimeException) {
            null
        } ?: return null
        val pkg = root.packageName?.toString()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            root.recycle()
        }
        return pkg
    }

    /** Explicit user tap: the only trigger for a (single, bounded) screen snapshot. */
    private fun onBubbleTapped() {
        val source = captureSource
        scope?.launch { panelCoordinator.onBubbleTapped(source) }
    }

    private val panelCallbacks = object : PanelCallbacks {
        override fun onClose() = panelCoordinator.dismiss()
        override fun onAction(action: AiAction) = panelCoordinator.onActionSelected(action)
        override fun onPreviewCancel() = panelCoordinator.onPreviewBack()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (lastOrientation != null && newConfig.orientation != lastOrientation) {
            // Rotation invalidates bounds and context: close and purge (ARCHITECTURE.md §6).
            panelCoordinator.dismiss()
        }
        lastOrientation = newConfig.orientation
        overlay?.relayout()
    }

    override fun onInterrupt() {
        // No LLM request exists in M2; a capture is short and bounded by its own time budget.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        coordinator.onServiceDisconnected()
        Log.i(TAG, "service_unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        if (::coordinator.isInitialized) coordinator.onServiceDisconnected()
        Log.i(TAG, "service_destroyed")
        super.onDestroy()
    }

    private fun teardown() {
        if (::panelCoordinator.isInitialized) panelCoordinator.dismiss()
        scope?.cancel()
        scope = null
        panel?.hide()
        panel = null
        captureSource = null
        overlay?.release()
        overlay = null
        lockMonitor?.stop()
        lockMonitor = null
    }

    private companion object {
        const val TAG = "BB/Service"
    }
}
