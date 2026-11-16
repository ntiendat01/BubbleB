package com.example.bubbleb.domain

/** Version of the privacy disclosure the user must accept (SECURITY_PRIVACY.md §2). */
object ConsentPolicy {
    const val CURRENT_VERSION: Int = 1
}

/** Bubble geometry, in dp, and layout tuning (PRODUCT_SPEC.md §8). */
object BubbleDefaults {
    const val SIZE_DP: Int = 48
    const val EDGE_MARGIN_DP: Int = 8
    const val DEFAULT_Y_FRACTION: Float = 0.35f

    /** Used only on API 26–29 where WindowMetrics insets are unavailable. */
    const val LEGACY_TOP_INSET_DP: Int = 32
    const val LEGACY_BOTTOM_INSET_DP: Int = 56
}

/**
 * Packages over which the bubble is never shown (fail closed).
 *
 * This list is best-effort and NOT a substitute for user configuration (SECURITY_PRIVACY.md §4);
 * package IDs must be verified on the real device. User-managed exclusions come from Room (M5).
 */
object ExclusionDefaults {
    /** System surfaces: lock screen / shade, permission and install dialogs. */
    val SYSTEM_HIDDEN_PACKAGES: Set<String> = setOf(
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    /** Default-deny for reliably identified sensitive categories (authenticator, password, wallet, bank). */
    val DEFAULT_SENSITIVE_PACKAGES: Set<String> = setOf(
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.x8bit.bitwarden",
        "com.agilebits.onepassword",
        "com.lastpass.lpandroid",
        "com.google.android.apps.walletnfcrel",
        "com.mservice.momotransfer",
        "vn.com.vng.zalopay",
        "com.VCB",
        "com.vietinbank.ipay",
        "com.mbmobile",
        "vn.com.techcombank.bb.app",
    )
}

/** Injectable wall clock so time-dependent logic stays testable. */
fun interface Clock {
    fun nowMs(): Long
}
