package com.example.bubbleb.service

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo

/** Some editors expose a password variation without setting the accessibility password flag. */
internal fun AccessibilityNodeInfo.isSensitiveInput(): Boolean {
    if (isPassword) return true
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}
