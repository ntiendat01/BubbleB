package com.example.bubbleb.capture

import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.capture.ScreenNode
import com.example.bubbleb.domain.model.IntRect

/** Pure-JVM stand-in for AccessibilityNodeInfo; counts reads to prove fail-closed ordering. */
class FakeScreenNode(
    override val packageName: String? = ScreenFixtures.PKG,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val isShowingHintText: Boolean = false,
    override val isPassword: Boolean = false,
    override val isVisibleToUser: Boolean = true,
    override val isEditable: Boolean = false,
    override val isFocused: Boolean = false,
    override val bounds: IntRect = IntRect(0, 0, 100, 10),
    override val selectionStart: Int = -1,
    override val selectionEnd: Int = -1,
    override val windowId: Int = -1,
    override val uniqueId: String? = null,
    val children: List<FakeScreenNode> = emptyList(),
    private val onChild: () -> Unit = {},
) : ScreenNode {
    override val childCount: Int get() = children.size
    override fun child(index: Int): ScreenNode? {
        onChild()
        return children.getOrNull(index)
    }
}

/** Advances by [stepMs] on every read: lets tests trigger the time budget deterministically. */
class SteppingClock(private val stepMs: Long) : Clock {
    private var now = 0L
    override fun nowMs(): Long = now.also { now += stepMs }
}

/** Fixture set required by docs/TASKS.md M2 (chat, long doc, empty, no input, protected, complex). */
object ScreenFixtures {
    const val PKG = "com.example.chat"

    private fun row(y: Int) = IntRect(0, y * 20, 1000, y * 20 + 18)

    fun chat(draft: String = "Mình đang tới") = FakeScreenNode(
        children = listOf(
            FakeScreenNode(text = "Lan", bounds = row(0)),
            FakeScreenNode(text = "Tối nay 19:30 đi ăn không?", bounds = row(1)),
            FakeScreenNode(text = "Gọi mình 0912 345 678 nhé", bounds = row(2)),
            FakeScreenNode(contentDescription = "Gửi", bounds = row(4)),
            FakeScreenNode(text = draft, isEditable = true, isFocused = true, bounds = row(3), selectionStart = 2, selectionEnd = 2),
        ),
    )

    fun longDocument(paragraphs: Int = 40) = FakeScreenNode(
        children = (0 until paragraphs).map { i ->
            FakeScreenNode(text = "Đoạn $i: " + "Nội dung tài liệu dài để kiểm tra giới hạn. ".repeat(6), bounds = row(i))
        },
    )

    fun emptyTree() = FakeScreenNode(children = listOf(FakeScreenNode(), FakeScreenNode(text = "   ")))

    fun noFocusedInput() = FakeScreenNode(
        children = listOf(
            FakeScreenNode(text = "Tin tức hôm nay", bounds = row(0)),
            FakeScreenNode(text = "", isEditable = true, isFocused = false, bounds = row(1)),
        ),
    )

    /** Protected/custom-rendered view: nodes exist but expose no text (e.g. FLAG_SECURE, canvas). */
    fun protectedView() = FakeScreenNode(children = listOf(FakeScreenNode(children = listOf(FakeScreenNode()))))

    fun withPassword() = FakeScreenNode(
        children = listOf(
            FakeScreenNode(text = "Đăng nhập", bounds = row(0)),
            FakeScreenNode(text = "hunter2", isEditable = true, isPassword = true, bounds = row(1)),
        ),
    )

    /** Wide tree with far more than 500 nodes. */
    fun wide(count: Int = 2_000) = FakeScreenNode(
        children = (0 until count).map { FakeScreenNode(text = "n$it", bounds = row(it)) },
    )

    /** Linear chain deeper than the depth cap. */
    fun deep(depth: Int = 60): FakeScreenNode {
        var node = FakeScreenNode(text = "level $depth", bounds = row(depth))
        for (d in depth - 1 downTo 0) node = FakeScreenNode(text = "level $d", bounds = row(d), children = listOf(node))
        return node
    }
}
