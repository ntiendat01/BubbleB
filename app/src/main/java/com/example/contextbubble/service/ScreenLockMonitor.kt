package com.example.contextbubble.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Tracks screen-off / unlock broadcasts (no permission needed). Hiding on lock is a policy, not a
 * guarantee that lock state is always inferred instantly (PRODUCT_SPEC.md §8).
 */
class ScreenLockMonitor(
    private val context: Context,
    private val onLockChanged: (Boolean) -> Unit,
) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private var receiver: BroadcastReceiver? = null

    fun isLockedNow(): Boolean = keyguard?.isKeyguardLocked ?: false

    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onLockChanged(true)
                    Intent.ACTION_USER_PRESENT -> onLockChanged(false)
                    Intent.ACTION_SCREEN_ON -> onLockChanged(isLockedNow())
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    fun stop() {
        val r = receiver ?: return
        receiver = null
        try {
            context.unregisterReceiver(r)
        } catch (_: IllegalArgumentException) {
            // Already unregistered.
        }
    }
}
