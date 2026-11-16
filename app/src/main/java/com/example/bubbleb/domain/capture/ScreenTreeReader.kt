package com.example.bubbleb.domain.capture

import com.example.bubbleb.domain.CaptureLimits
import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.model.CaptureStats
import com.example.bubbleb.domain.model.ElementRole
import com.example.bubbleb.domain.model.InputSnapshot
import com.example.bubbleb.domain.model.ScreenContext
import com.example.bubbleb.domain.model.ScreenElement
import com.example.bubbleb.domain.text.TextLimits

/** Caps for one traversal; defaults come from [CaptureLimits]. */
data class TraversalLimits(
    val maxNodes: Int = CaptureLimits.MAX_NODES,
    val maxDepth: Int = CaptureLimits.MAX_DEPTH,
    val timeBudgetMs: Long = CaptureLimits.TIME_BUDGET_MS,
    val maxNodeCodePoints: Int = CaptureLimits.MAX_NODE_CODE_POINTS,
    val maxTotalCodePoints: Int = CaptureLimits.MAX_PAYLOAD_CODE_POINTS,
)

/**
 * Bounded, iterative (no recursion) DFS over a [ScreenNode] tree producing an immutable
 * [ScreenContext]. Fails closed: any password node blocks the whole capture; invisible and
 * foreign-package subtrees are skipped; nothing found -> [CaptureFailure.NO_TEXT].
 * Ordering by bounds is a heuristic and does not reconstruct conversations or senders.
 */
class ScreenTreeReader(
    private val elapsedClock: Clock,
    private val limits: TraversalLimits = TraversalLimits(),
) {
    private class Frame(val node: ScreenNode, val depth: Int)

    private class Accumulator {
        val elements = mutableListOf<ScreenElement>()
        var focusedInput: InputSnapshot? = null
        var visited = 0
        var maxDepth = 0
        var totalCodePoints = 0
        var hitNodeCap = false
        var hitDepthCap = false
        var hitTimeBudget = false
        var truncated = false
    }

    fun read(root: ScreenNode, packageName: String, appLabel: String, capturedAtMs: Long): CaptureResult {
        val acc = Accumulator()
        val startMs = elapsedClock.nowMs()
        val stack = ArrayDeque<Frame>().apply { addLast(Frame(root, 0)) }
        while (stack.isNotEmpty()) {
            if (acc.visited >= limits.maxNodes) { acc.hitNodeCap = true; break }
            if (elapsedClock.nowMs() - startMs > limits.timeBudgetMs) { acc.hitTimeBudget = true; break }
            val frame = stack.removeLast()
            val node = frame.node
            acc.visited++
            acc.maxDepth = maxOf(acc.maxDepth, frame.depth)
            if (node.isPassword) return CaptureResult.Failure(CaptureFailure.SENSITIVE_SCREEN)
            if (!node.isVisibleToUser) continue
            val nodePkg = node.packageName
            if (nodePkg != null && nodePkg != packageName) continue
            collect(node, acc)
            pushChildren(node, frame.depth, stack, acc)
        }
        val ordered = dedupConsecutive(
            acc.elements.sortedWith(compareBy<ScreenElement>({ it.bounds.top }, { it.bounds.left }, { it.order })),
        )
        if (ordered.none { it.role != ElementRole.INPUT }) return CaptureResult.Failure(CaptureFailure.NO_TEXT)
        val context = ScreenContext(packageName, appLabel, ordered, acc.focusedInput, capturedAtMs)
        val stats = CaptureStats(acc.visited, acc.maxDepth, acc.hitNodeCap, acc.hitDepthCap, acc.hitTimeBudget, acc.truncated)
        return CaptureResult.Success(context, stats)
    }

    private fun pushChildren(node: ScreenNode, depth: Int, stack: ArrayDeque<Frame>, acc: Accumulator) {
        val count = node.childCount
        if (count <= 0) return
        if (depth + 1 > limits.maxDepth) { acc.hitDepthCap = true; return }
        // Reverse push keeps document order when popping.
        for (i in count - 1 downTo 0) node.child(i)?.let { stack.addLast(Frame(it, depth + 1)) }
    }

    private fun collect(node: ScreenNode, acc: Accumulator) {
        if (node.isEditable) {
            val draft = if (node.isShowingHintText) "" else node.text.orEmpty()
            if (node.isFocused && acc.focusedInput == null) {
                acc.focusedInput = InputSnapshot(
                    text = draft,
                    selectionStart = node.selectionStart.takeIf { it >= 0 },
                    selectionEnd = node.selectionEnd.takeIf { it >= 0 },
                    isPassword = false,
                )
            }
            addElement(draft, ElementRole.INPUT, node, acc)
            return
        }
        val text = node.text?.takeIf { it.isNotBlank() }
        // contentDescription only when no text: it may hold sensitive hints and is redacted later.
        val (value, role) = if (text != null) text to ElementRole.TEXT else node.contentDescription.orEmpty() to ElementRole.LABEL
        addElement(value, role, node, acc)
    }

    private fun addElement(raw: String, role: ElementRole, node: ScreenNode, acc: Accumulator) {
        val normalized = TextLimits.normalize(raw)
        if (normalized.isEmpty()) return
        val remaining = limits.maxTotalCodePoints - acc.totalCodePoints
        if (remaining <= 0) { acc.truncated = true; return }
        val cap = minOf(limits.maxNodeCodePoints, remaining)
        val value = TextLimits.truncate(normalized, cap)
        if (value.length < normalized.length) acc.truncated = true
        acc.totalCodePoints += TextLimits.codePointLength(value)
        acc.elements += ScreenElement(value, role, node.bounds, acc.elements.size, node.isEditable)
    }

    /** Conservative: drops only exact consecutive duplicates of the same role. */
    private fun dedupConsecutive(list: List<ScreenElement>): List<ScreenElement> =
        list.filterIndexed { i, e -> i == 0 || list[i - 1].text != e.text || list[i - 1].role != e.role }
}
