package com.example.bubbleb.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.bubbleb.R
import com.example.bubbleb.domain.model.BubbleVisibility
import com.example.bubbleb.overlay.OverlayController
import com.example.bubbleb.presentation.BubbleCoordinator
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
 * [BubbleCoordinator] and renders the bubble via [OverlayController]. It never reads screen text,
 * never calls the network and never persists state decisions itself.
 */
@AndroidEntryPoint
class BubbleAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: BubbleCoordinator

    private var scope: CoroutineScope? = null
    private var overlay: OverlayController? = null
    private var lockMonitor: ScreenLockMonitor? = null

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
        val monitor = ScreenLockMonitor(this) { locked -> coordinator.onScreenLockChanged(locked) }
        lockMonitor = monitor
        monitor.start()

        coordinator.onServiceConnected()
        coordinator.onScreenLockChanged(monitor.isLockedNow())
        refreshForeground(eventPackage = null)

        serviceScope.launch {
            combine(coordinator.bubbleVisibility, coordinator.bubblePosition) { v, p -> v to p }
                .collect { (visibility, position) ->
                    if (visibility is BubbleVisibility.Visible) controller.show(position) else controller.hide()
                }
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
        coordinator.onForegroundPackageChanged(rootPackage ?: eventPackage)
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

    private fun onBubbleTapped() {
        // M1 proof-of-concept: no capture, no network. Screen capture arrives in M2.
        Toast.makeText(this, R.string.bubble_tap_placeholder, Toast.LENGTH_SHORT).show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.relayout()
    }

    override fun onInterrupt() {
        // No in-flight capture/LLM work exists in M1. From M2 this cancels active jobs.
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
        scope?.cancel()
        scope = null
        overlay?.release()
        overlay = null
        lockMonitor?.stop()
        lockMonitor = null
    }

    private companion object {
        const val TAG = "BB/Service"
    }
}
