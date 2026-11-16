package com.example.bubbleb.audit

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static guards for AGENTS.md §3–4: minimal permissions, HTTPS only, least-privilege service. */
class ManifestAuditTest {
    private fun read(path: String): String {
        val f = listOf(File(path), File("app/$path")).first { it.exists() }
        // Strip XML comments so explanatory notes don't affect the checks.
        return f.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    private val manifest by lazy { read("src/main/AndroidManifest.xml") }

    @Test
    fun onlyInternetPermissionRequested() {
        val perms = Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.INTERNET"), perms)
        assertFalse(manifest.contains("SYSTEM_ALERT_WINDOW"))
        assertFalse(manifest.contains("QUERY_ALL_PACKAGES"))
    }

    @Test
    fun accessibilityServiceProtectedByBindPermission() {
        assertTrue(manifest.contains("android:permission=\"android.permission.BIND_ACCESSIBILITY_SERVICE\""))
        assertTrue(manifest.contains("android.accessibilityservice.AccessibilityService"))
    }

    @Test
    fun backupDisabled() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun networkConfig_noCleartext() {
        val cfg = read("src/main/res/xml/network_security_config.xml")
        assertTrue(cfg.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(cfg.contains("cleartextTrafficPermitted=\"true\""))
        assertFalse(cfg.contains("src=\"user\""))
        assertFalse(manifest.contains("usesCleartextTraffic=\"true\""))
    }

    @Test
    fun accessibilityConfig_leastPrivilege() {
        val cfg = read("src/main/res/xml/accessibility_service_config.xml")
        assertFalse(cfg.contains("flagRetrieveInteractiveWindows"))
        assertFalse(cfg.contains("typeAllMask"))
        assertTrue(cfg.contains("typeWindowStateChanged"))
        assertTrue(cfg.contains("canRetrieveWindowContent=\"true\""))
    }
}
