package com.ead.koog.context.orchestrator.compression

import ai.koog.prompt.message.Message

/**
 * Independent token estimator used when model-side token metadata is missing.
 */
fun interface TokenEstimator {
    fun estimate(messages: List<Message>): Int
}

class HeuristicTokenEstimator(
    private val charsPerToken: Int = 4,
    private val perMessageOverhead: Int = 8,
) : TokenEstimator {
    override fun estimate(messages: List<Message>): Int {
        if (messages.isEmpty()) return 0
        val contentChars = messages.sumOf { it.content.length }
        val roleOverhead = messages.size * perMessageOverhead
        return ((contentChars + roleOverhead).toDouble() / charsPerToken)
            .toInt()
            .coerceAtLeast(1)
    }
}
