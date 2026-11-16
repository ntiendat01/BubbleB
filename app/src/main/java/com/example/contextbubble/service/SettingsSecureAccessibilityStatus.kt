package com.example.contextbubble.service

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.example.contextbubble.domain.policy.EnabledServicesParser
import com.example.contextbubble.domain.repository.AccessibilityStatusSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes whether Android Settings lists [BubbleAccessibilityService] as enabled. This is the OS
 * view; the dashboard combines it with the real service connection state (no fake success).
 */
@Singleton
class SettingsSecureAccessibilityStatus @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AccessibilityStatusSource {

    override val isEnabledInSettings: Flow<Boolean> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(readEnabled())
            }
        }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer,
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, observer,
        )
        trySend(readEnabled())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().distinctUntilChanged()

    private fun readEnabled(): Boolean {
        val raw = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        return EnabledServicesParser.isServiceEnabled(
            raw = raw,
            packageName = context.packageName,
            className = BubbleAccessibilityService::class.java.name,
        )
    }
}
