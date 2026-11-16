package com.example.bubbleb.capture

import com.example.bubbleb.domain.capture.CapturePreflight
import com.example.bubbleb.domain.model.CaptureFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapturePreflightTest {
    private fun check(pkg: String?, excluded: Set<String> = emptySet(), locked: Boolean = false) =
        CapturePreflight.check(pkg, "com.example.bubbleb", excluded, locked)

    @Test fun normalAppAllowed() = assertNull(check("com.zing.zalo"))
    @Test fun nullOrBlankPackageFailsClosed() {
        assertEquals(CaptureFailure.UNKNOWN_PACKAGE, check(null))
        assertEquals(CaptureFailure.UNKNOWN_PACKAGE, check("  "))
    }
    @Test fun ownAppBlocked() = assertEquals(CaptureFailure.OWN_APP, check("com.example.bubbleb"))
    @Test fun systemUiBlocked() = assertEquals(CaptureFailure.EXCLUDED_APP, check("com.android.systemui"))
    @Test fun userExclusionBlocked() = assertEquals(CaptureFailure.EXCLUDED_APP, check("com.bank", setOf("com.bank")))
    @Test fun lockedBlocksEverything() = assertEquals(CaptureFailure.SCREEN_LOCKED, check("com.zing.zalo", locked = true))
}
