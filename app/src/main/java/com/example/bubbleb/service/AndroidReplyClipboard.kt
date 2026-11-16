package com.example.bubbleb.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import com.example.bubbleb.domain.reply.ReplyClipboard

/** No clipboard reads, persistence or automatic fallback. Caller must obtain explicit confirmation. */
class AndroidReplyClipboard(context: Context) : ReplyClipboard {
    private val manager = context.getSystemService(ClipboardManager::class.java)

    override fun copy(text: String): Boolean {
        if (text.isEmpty() || manager == null) return false
        return try {
            val clip = ClipData.newPlainText("BubbleB", text)
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            manager.setPrimaryClip(clip)
            true
        } catch (_: RuntimeException) {
            false
        }
    }
}
