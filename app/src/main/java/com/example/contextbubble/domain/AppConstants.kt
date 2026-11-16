package com.example.contextbubble.domain

/** Version of the privacy disclosure the user must accept (SECURITY_PRIVACY.md §2). */
object ConsentPolicy {
    const val CURRENT_VERSION: Int = 1
}

/** Injectable wall clock so time-dependent logic stays testable. */
fun interface Clock {
    fun nowMs(): Long
}
