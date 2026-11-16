package com.example.bubbleb.domain.capture

import com.example.bubbleb.domain.model.IntRect

/**
 * Platform-free, read-only view of one accessibility node, valid only during a single capture.
 * The Android adapter wraps AccessibilityNodeInfo; tests use in-memory fixtures. Implementations
 * must not be retained after [ScreenTreeReader.read] returns.
 */
interface ScreenNode {
    val packageName: String?
    val text: String?
    val contentDescription: String?
    val isShowingHintText: Boolean
    val isPassword: Boolean
    val isVisibleToUser: Boolean
    val isEditable: Boolean
    val isFocused: Boolean
    val bounds: IntRect
    val selectionStart: Int
    val selectionEnd: Int
    val childCount: Int

    /** May return null when the child vanished or is inaccessible. */
    fun child(index: Int): ScreenNode?
}
