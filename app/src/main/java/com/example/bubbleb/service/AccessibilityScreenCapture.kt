package com.example.bubbleb.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.capture.ScreenTreeReader
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.repository.ScreenCaptureSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    override suspend fun capture(preflight: (String?) -> CaptureFailure?): CaptureResult {
        var result = captureOnce(preflight)
        var attempt = 1
        // Right after the tap our own overlay can still be the "active" (touched) window; the
        // system restores the app window shortly after the touch ends, so retry briefly.
        while (attempt < OWN_ROOT_ATTEMPTS && result.isOwnOrMissingRoot()) {
            Log.d(TAG, "capture_retry:${(result as CaptureResult.Failure).reason},attempt=$attempt")
            delay(OWN_ROOT_RETRY_MS)
            result = captureOnce(preflight)
            attempt++
        }
        Log.d(TAG, "capture_done:" + result.stage())
        return result
    }

    private suspend fun captureOnce(preflight: (String?) -> CaptureFailure?): CaptureResult =
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

    private fun CaptureResult.isOwnOrMissingRoot(): Boolean =
        this is CaptureResult.Failure && (reason == CaptureFailure.OWN_APP || reason == CaptureFailure.NO_ROOT)

    /** Enum/count-only summary; never text or package names. */
    private fun CaptureResult.stage(): String = when (this) {
        is CaptureResult.Failure -> "failure:$reason"
        is CaptureResult.Success ->
            "ok:elements=${context.elements.size},nodes=${stats.visitedNodes},caps=" +
                "${stats.hitNodeCap}/${stats.hitDepthCap}/${stats.hitTimeBudget}"
    }

    /** Package visibility may hide labels on Android 11+; fall back to the package name. */
    private fun appLabel(pkg: String): String = try {
        val pm = service.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        const val TAG = "BB/Service"
        const val OWN_ROOT_ATTEMPTS = 3
        const val OWN_ROOT_RETRY_MS = 120L
    }
}
