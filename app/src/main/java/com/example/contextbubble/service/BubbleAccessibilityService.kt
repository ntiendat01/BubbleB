package com.example.contextbubble.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * M0: declaration-only accessibility service so the manifest/config can be validated.
 * It intentionally does nothing: no overlay, no screen reading, no network. Status observation,
 * overlay and lifecycle cleanup are implemented in M1.
 */
class BubbleAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty in M0. Event content is never read.
    }

    override fun onInterrupt() {
        // Nothing to interrupt in M0.
    }
}
