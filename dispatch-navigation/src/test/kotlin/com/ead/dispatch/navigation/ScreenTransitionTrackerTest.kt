package com.ead.dispatch.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenTransitionTrackerTest {
    @Test
    fun `reports only content key changes`() {
        val tracker = ScreenTransitionTracker("home")

        assertFalse(tracker.update("home"))
        assertTrue(tracker.update("chat"))
        assertFalse(tracker.update("chat"))
        assertTrue(tracker.update("home"))
    }
}
