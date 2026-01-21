package com.ead.dispatch.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveAreaBoundaryTrackerTest {
    @Test
    fun `does not reset on first render or same size`() {
        val tracker = ActiveAreaBoundaryTracker()

        assertFalse(tracker.shouldReset(3))
        assertFalse(tracker.shouldReset(3))
    }

    @Test
    fun `resets when active area line count changes`() {
        val tracker = ActiveAreaBoundaryTracker()

        tracker.shouldReset(2)
        assertTrue(tracker.shouldReset(4))
        assertFalse(tracker.shouldReset(4))
    }
}
