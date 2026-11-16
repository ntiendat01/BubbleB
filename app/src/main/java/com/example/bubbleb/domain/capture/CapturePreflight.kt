package com.example.bubbleb.domain.capture

import com.example.bubbleb.domain.ExclusionDefaults
import com.example.bubbleb.domain.model.CaptureFailure

/** Checks run BEFORE any node text is read. Returns null only when capture may proceed. */
object CapturePreflight {
    fun check(
        packageName: String?,
        ownPackage: String,
        userExcludedPackages: Set<String>,
        screenLocked: Boolean,
    ): CaptureFailure? {
        val pkg = packageName?.trim().orEmpty()
        return when {
            screenLocked -> CaptureFailure.SCREEN_LOCKED
            pkg.isEmpty() -> CaptureFailure.UNKNOWN_PACKAGE
            pkg == ownPackage -> CaptureFailure.OWN_APP
            pkg in ExclusionDefaults.SYSTEM_HIDDEN_PACKAGES -> CaptureFailure.EXCLUDED_APP
            pkg in ExclusionDefaults.DEFAULT_SENSITIVE_PACKAGES -> CaptureFailure.EXCLUDED_APP
            pkg in userExcludedPackages -> CaptureFailure.EXCLUDED_APP
            else -> null
        }
    }
}
