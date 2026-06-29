package com.ead.dispatch.sample.widgets

import kotlin.test.Test
import kotlin.test.assertEquals

class DiffApprovalTest {
    @Test
    fun `pending expiry boundary becomes expired and not pending`() {
        val now = 8_000L
        val expiry = 1_000L
        val config =
            FileChangeApprovalConfig(
                pendingRanges =
                    listOf(
                        PendingLineRange(
                            startLine = 1,
                            endLine = 1,
                            changedAtEpochMillis = now - expiry,
                        ),
                    ),
                expiryMillis = expiry,
            )

        assertEquals(emptySet(), resolvePendingLines(config, nowEpochMillis = now))
    }

    @Test
    fun `non expired range marks its lines pending`() {
        val now = 8_000L
        val expiry = 1_000L
        val config =
            FileChangeApprovalConfig(
                pendingRanges =
                    listOf(
                        PendingLineRange(
                            startLine = 2,
                            endLine = 4,
                            changedAtEpochMillis = now - (expiry - 1),
                        ),
                    ),
                expiryMillis = expiry,
            )

        assertEquals(setOf(2, 3, 4), resolvePendingLines(config, nowEpochMillis = now))
    }
}
