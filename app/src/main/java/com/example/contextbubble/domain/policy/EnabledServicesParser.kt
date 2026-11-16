package com.example.contextbubble.domain.policy

/**
 * Parses `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` (colon separated flattened component
 * names). Accepts both full (`pkg/pkg.Cls`) and short (`pkg/.Cls`) class forms.
 */
object EnabledServicesParser {
    private const val SEPARATOR = ':'

    fun isServiceEnabled(raw: String?, packageName: String, className: String): Boolean {
        if (raw.isNullOrBlank()) return false
        val shortClass = if (className.startsWith(packageName)) className.removePrefix(packageName) else null
        return raw.split(SEPARATOR).any { entry ->
            val parts = entry.trim().split('/')
            if (parts.size != 2 || !parts[0].equals(packageName, ignoreCase = true)) return@any false
            val cls = parts[1]
            cls.equals(className, ignoreCase = true) ||
                (shortClass != null && cls.equals(shortClass, ignoreCase = true))
        }
    }
}
