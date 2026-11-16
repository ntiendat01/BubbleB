package com.example.bubbleb.reply

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.service.AndroidReplyClipboard
import com.example.bubbleb.service.isSensitiveInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 35])
class AndroidReplyAdaptersTest {
    @Test fun copyReplacesOnlyOnDirectAdapterCall_andMarksContentSensitive() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("fixture", "previous synthetic text"))
        val adapter = AndroidReplyClipboard(context)
        assertEquals("previous synthetic text", manager.primaryClip!!.getItemAt(0).text)
        assertTrue(adapter.copy("new synthetic text"))
        assertEquals("new synthetic text", manager.primaryClip!!.getItemAt(0).text)
        assertTrue(manager.primaryClipDescription!!.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test fun passwordInputVariationsAreBlockedEvenWithoutAccessibilityFlag() {
        val types = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )
        types.forEach { type ->
            @Suppress("DEPRECATION")
            val node = AccessibilityNodeInfo.obtain()
            node.isPassword = false
            node.inputType = type
            assertTrue(node.isSensitiveInput())
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}
