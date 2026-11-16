package com.example.bubbleb.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.bubbleb.domain.capture.ScreenNode
import com.example.bubbleb.domain.model.IntRect

/**
 * Tracks every AccessibilityNodeInfo obtained during ONE capture so all of them are released at
 * the end. No node reference escapes the capture (AGENTS.md §2).
 */
class NodeSession {
    private val obtained = mutableListOf<AccessibilityNodeInfo>()

    fun wrap(info: AccessibilityNodeInfo): ScreenNode {
        obtained += info
        return AccessibilityScreenNode(info, this)
    }

    fun releaseAll() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            obtained.forEach { runCatching { it.recycle() } }
        }
        obtained.clear()
    }
}

/** Read-only adapter; values are read lazily and only for nodes the bounded traversal visits. */
private class AccessibilityScreenNode(
    private val info: AccessibilityNodeInfo,
    private val session: NodeSession,
) : ScreenNode {
    override val packageName: String? get() = info.packageName?.toString()
    override val text: String? get() = info.text?.toString()
    override val contentDescription: String? get() = info.contentDescription?.toString()
    override val isShowingHintText: Boolean get() = info.isShowingHintText
    override val isPassword: Boolean get() = info.isSensitiveInput()
    override val windowId: Int get() = info.windowId
    override val uniqueId: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) info.uniqueId else null
    override val isVisibleToUser: Boolean get() = info.isVisibleToUser
    override val isEditable: Boolean get() = info.isEditable
    override val isFocused: Boolean get() = info.isFocused
    override val bounds: IntRect
        get() = Rect().also(info::getBoundsInScreen).let { IntRect(it.left, it.top, it.right, it.bottom) }
    override val selectionStart: Int get() = info.textSelectionStart
    override val selectionEnd: Int get() = info.textSelectionEnd
    override val childCount: Int get() = info.childCount

    override fun child(index: Int): ScreenNode? =
        runCatching { info.getChild(index) }.getOrNull()?.let(session::wrap)
}
