package com.ead.dispatch.sample.widgets

/**
 * App-side approval/expiry overlay for diff review.
 *
 * The library [com.ead.dispatch.widget.FileDiff] is approval-agnostic: it accepts an
 * already-resolved set of pending after-text line numbers. The time/expiry policy lives here,
 * in the sample, because only the app knows "now" and the expiry rule.
 *
 * Pending ranges target line numbers in the **after** text (1-based, inclusive).
 */
data class FileChangeApprovalConfig(
    val pendingRanges: List<PendingLineRange>,
    val expiryMillis: Long = DEFAULT_EXPIRY_MILLIS,
) {
    init {
        require(expiryMillis > 0) { "expiryMillis must be > 0" }
    }

    companion object {
        const val DEFAULT_EXPIRY_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
    }
}

/** Pending range metadata for approval workflows. */
data class PendingLineRange(
    val startLine: Int,
    val endLine: Int,
    val changedAtEpochMillis: Long,
) {
    init {
        require(startLine > 0) { "startLine must be > 0" }
        require(endLine >= startLine) { "endLine must be >= startLine" }
    }
}

/**
 * Resolve which after-text line numbers are currently pending (not yet expired) at
 * [nowEpochMillis]. A line is pending when the newest range covering it has not yet reached
 * its expiry window. This is the time logic that used to live inside the widget's `resolveRows`.
 */
fun resolvePendingLines(
    config: FileChangeApprovalConfig,
    nowEpochMillis: Long,
): Set<Int> {
    val candidateLines =
        config.pendingRanges
            .flatMap { it.startLine..it.endLine }
            .toSet()
    val pending = mutableSetOf<Int>()
    for (line in candidateLines) {
        val newest = newestPendingTimestampForLine(line, config.pendingRanges) ?: continue
        if (nowEpochMillis - newest < config.expiryMillis) {
            pending += line
        }
    }
    return pending
}

internal fun newestPendingTimestampForLine(
    lineNumber: Int,
    ranges: List<PendingLineRange>,
): Long? {
    var newest: Long? = null
    for (range in ranges) {
        if (lineNumber in range.startLine..range.endLine) {
            newest = maxOf(newest ?: Long.MIN_VALUE, range.changedAtEpochMillis)
        }
    }
    return newest
}
