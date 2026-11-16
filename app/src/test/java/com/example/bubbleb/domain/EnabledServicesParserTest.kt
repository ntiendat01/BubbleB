package com.example.bubbleb.domain

import com.example.bubbleb.domain.policy.EnabledServicesParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnabledServicesParserTest {
    private val pkg = "com.example.bubbleb"
    private val cls = "com.example.bubbleb.service.BubbleAccessibilityService"

    private fun enabled(raw: String?) = EnabledServicesParser.isServiceEnabled(raw, pkg, cls)

    @Test
    fun nullOrBlank_isDisabled() {
        assertFalse(enabled(null))
        assertFalse(enabled(""))
        assertFalse(enabled("   "))
    }

    @Test
    fun fullForm_isEnabled() {
        assertTrue(enabled("$pkg/$cls"))
    }

    @Test
    fun shortForm_isEnabled() {
        assertTrue(enabled("$pkg/.service.BubbleAccessibilityService"))
    }

    @Test
    fun amongOtherServices_isEnabled() {
        assertTrue(enabled("com.other/com.other.Svc:$pkg/$cls:com.x/.Y"))
    }

    @Test
    fun otherServicesOnly_isDisabled() {
        assertFalse(enabled("com.other/com.other.Svc:com.x/.Y"))
    }

    @Test
    fun samePackageDifferentClass_isDisabled() {
        assertFalse(enabled("$pkg/$pkg.service.OtherService"))
    }

    @Test
    fun prefixPackageSpoof_isDisabled() {
        assertFalse(enabled("${pkg}evil/$cls"))
    }

    @Test
    fun malformedEntries_ignored() {
        assertFalse(enabled("garbage::/:$pkg"))
    }
}
