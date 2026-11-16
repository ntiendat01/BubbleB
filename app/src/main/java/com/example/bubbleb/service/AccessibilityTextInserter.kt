package com.example.bubbleb.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.model.InputSnapshot
import com.example.bubbleb.domain.model.InputTarget
import com.example.bubbleb.domain.reply.EditableTarget
import com.example.bubbleb.domain.reply.EditorSession
import com.example.bubbleb.domain.reply.EditorSessionSource
import com.example.bubbleb.domain.reply.GuardedTextInserter
import com.example.bubbleb.domain.reply.InsertionResult
import com.example.bubbleb.domain.reply.TextInserter

/** Android boundary only; the synchronous session releases every node before returning. */
class AccessibilityTextInserter(service: AccessibilityService) : TextInserter {
    private val guarded = GuardedTextInserter(
        EditorSessionSource { AccessibilityEditorSession(service) },
        Clock { SystemClock.elapsedRealtime() },
    )

    override fun insert(snapshot: InputSnapshot, text: String): InsertionResult = guarded.insert(snapshot, text)
}

private class AccessibilityEditorSession(private val service: AccessibilityService) : EditorSession {
    private val nodes = mutableListOf<AccessibilityNodeInfo>()
    private var focused: AccessibilityNodeInfo? = null

    override fun isLocked(): Boolean = service.getSystemService(KeyguardManager::class.java)?.let {
        it.isKeyguardLocked || it.isDeviceLocked
    } ?: true

    override fun readFocused(): EditableTarget? {
        focused = null
        val root = service.rootInActiveWindow?.also(nodes::add) ?: return null
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.also(nodes::add) ?: return null
        if (!target.refresh()) return null
        if (root.packageName != target.packageName || root.windowId != target.windowId) return null
        val sensitive = target.isSensitiveInput()
        val description = EditableTarget(
            identity = target.identity(),
            text = if (sensitive) null else if (target.isShowingHintText) "" else target.text?.toString(),
            selectionStart = target.textSelectionStart,
            selectionEnd = target.textSelectionEnd,
            editable = target.isEditable,
            focused = target.isFocused,
            visible = target.isVisibleToUser,
            enabled = target.isEnabled,
            password = sensitive,
            supportsSetText = target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT },
        )
        focused = target
        return description
    }

    override fun setText(text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
    }

    private fun AccessibilityNodeInfo.identity(): InputTarget? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val id = uniqueId?.takeIf { it.isNotBlank() } ?: return null
        val pkg = packageName?.toString()?.takeIf { it.isNotBlank() } ?: return null
        return InputTarget(pkg, windowId, id)
    }

    override fun close() {
        focused = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            nodes.distinctBy { System.identityHashCode(it) }.forEach { runCatching { it.recycle() } }
        }
        nodes.clear()
    }
}
