package com.example.bubbleb.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.SystemClock
import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.capture.ScreenTreeReader
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.repository.ScreenCaptureSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android adapter for one user-initiated snapshot of `rootInActiveWindow`. Runs off the main
 * thread, checks the root package BEFORE reading any text, and releases all nodes afterwards.
 * Protected/secure/custom-rendered UIs simply expose no text and end as NO_TEXT (never bypassed).
 */
class AccessibilityScreenCapture(
    private val service: AccessibilityService,
    private val wallClock: Clock,
) : ScreenCaptureSource {

    private val reader = ScreenTreeReader(elapsedClock = Clock { SystemClock.elapsedRealtime() })

    override suspend fun capture(preflight: (String?) -> CaptureFailure?): CaptureResult =
        withContext(Dispatchers.Default) {
            val session = NodeSession()
            try {
                val rootInfo = runCatching { service.rootInActiveWindow }.getOrNull()
                    ?: return@withContext CaptureResult.Failure(CaptureFailure.NO_ROOT)
                val root = session.wrap(rootInfo)
                val pkg = root.packageName
                preflight(pkg)?.let { return@withContext CaptureResult.Failure(it) }
                if (pkg.isNullOrBlank()) return@withContext CaptureResult.Failure(CaptureFailure.UNKNOWN_PACKAGE)
                reader.read(root, pkg, appLabel(pkg), wallClock.nowMs())
            } finally {
                session.releaseAll()
            }
        }

    /** Package visibility may hide labels on Android 11+; fall back to the package name. */
    private fun appLabel(pkg: String): String = try {
        val pm = service.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }
}
