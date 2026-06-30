package io.github.darkryh.dispatch.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class DispatchRuntimeSignalsTest {
    @Test
    fun `window title applies when value changes`() {
        assertTrue(
            shouldApplyWindowTitle(
                enforceWindowTitle = false,
                lastWindowTitleApplied = "Old",
                requestedWindowTitle = "New",
                nowNanos = 10L,
                lastAppliedAtNanos = 5L,
            ),
        )
    }

    @Test
    fun `window title does not reapply without enforce flag`() {
        assertFalse(
            shouldApplyWindowTitle(
                enforceWindowTitle = false,
                lastWindowTitleApplied = "Dispatch",
                requestedWindowTitle = "Dispatch",
                nowNanos = 1_000_000_000L,
                lastAppliedAtNanos = 1L,
            ),
        )
    }

    @Test
    fun `window title enforce is throttled to interval`() {
        assertFalse(
            shouldApplyWindowTitle(
                enforceWindowTitle = true,
                lastWindowTitleApplied = "Dispatch",
                requestedWindowTitle = "Dispatch",
                nowNanos = 1_500_000_000L,
                lastAppliedAtNanos = 1_000_000_000L,
            ),
        )
        assertTrue(
            shouldApplyWindowTitle(
                enforceWindowTitle = true,
                lastWindowTitleApplied = "Dispatch",
                requestedWindowTitle = "Dispatch",
                nowNanos = 2_100_000_000L,
                lastAppliedAtNanos = 1_000_000_000L,
            ),
        )
    }

    @Test
    fun `exit key resolves immediately when double press disabled`() {
        assertTrue(
            shouldExitOnExitKey(
                requireExitDoublePress = false,
                exitPromptArmed = false,
                armDeadlineNanos = 0L,
                eventTimestampNanos = 10L,
            ),
        )
    }

    @Test
    fun `exit key requires second press when double press enabled`() {
        assertFalse(
            shouldExitOnExitKey(
                requireExitDoublePress = true,
                exitPromptArmed = false,
                armDeadlineNanos = 0L,
                eventTimestampNanos = 10L,
            ),
        )
    }

    @Test
    fun `second exit key uses event timestamp against arm deadline`() {
        assertTrue(
            shouldExitOnExitKey(
                requireExitDoublePress = true,
                exitPromptArmed = true,
                armDeadlineNanos = 2_000L,
                eventTimestampNanos = 1_500L,
            ),
        )
        assertFalse(
            shouldExitOnExitKey(
                requireExitDoublePress = true,
                exitPromptArmed = true,
                armDeadlineNanos = 2_000L,
                eventTimestampNanos = 2_100L,
            ),
        )
    }

    @Test
    fun `arm deadline uses timeout and guards overflow`() {
        assertEquals(
            2_000L,
            computeExitArmDeadlineNanos(
                eventTimestampNanos = 1_000L,
                timeout = 1_000.nanoseconds,
            ),
        )
        assertEquals(
            Long.MAX_VALUE,
            computeExitArmDeadlineNanos(
                eventTimestampNanos = Long.MAX_VALUE - 1,
                timeout = 10.nanoseconds,
            ),
        )
        assertEquals(
            Long.MAX_VALUE,
            computeExitArmDeadlineNanos(
                eventTimestampNanos = 1_000L,
                timeout = kotlin.time.Duration.INFINITE,
            ),
        )
        assertEquals(
            Long.MAX_VALUE,
            computeExitArmDeadlineNanos(
                eventTimestampNanos = 1_000L,
                timeout = 0.seconds,
            ),
        )
    }
}
