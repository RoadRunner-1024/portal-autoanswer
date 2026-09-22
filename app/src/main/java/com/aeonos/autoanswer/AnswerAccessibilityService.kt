package com.aeonos.autoanswer

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Fallback answerer: finds the Answer control in the incoming-call screen and clicks it.
 *
 * Only acts while [AutoAnswerService.answering] is set, so it can never click things of its own
 * accord — if Telecom answered the call, this never runs. It clicks Answer and nothing else; the
 * hang-up button is deliberately left alone so the person can always end the call themselves.
 */
class AnswerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoAnswer"

        /** Text/ids that mean "pick up". Matched case-insensitively against several node fields. */
        private val ANSWER_WORDS = listOf(
            "answer", "accept", "join", "pick up", "pickup", "reply",
        )
        /** Never press these, whatever else matches. */
        private val AVOID_WORDS = listOf(
            "decline", "reject", "ignore", "dismiss", "end", "hang",
        )
    }

    override fun onServiceConnected() {
        Log.i(TAG, "accessibility service connected")
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AutoAnswerService.answering) return
        val root = rootInActiveWindow ?: return
        val node = findAnswerNode(root) ?: return
        val clicked = clickNode(node)
        Log.i(TAG, "answer control found — click ${if (clicked) "sent" else "FAILED"}")
        if (clicked) {
            // Tell the service so it stops looking; also stops a second event re-clicking.
            AutoAnswerService.markAnswered()
        }
    }

    /** Depth-first search for something that looks like the Answer control. */
    private fun findAnswerNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val haystack = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.viewIdResourceName,
        ).joinToString(" ").lowercase()

        if (haystack.isNotBlank() &&
            ANSWER_WORDS.any { haystack.contains(it) } &&
            AVOID_WORDS.none { haystack.contains(it) }
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAnswerNode(child)?.let { return it }
        }
        return null
    }

    /** Click the node, or the nearest ancestor that will take a click. */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 6) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
            hops++
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
