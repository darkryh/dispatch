package com.ead.dispatch.runtime

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ScrollingContentTrackerTest {
    @Test
    fun `first consume returns all lines`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b")) shouldBe ScrollUpdate(listOf("a", "b"), reset = false)
    }

    @Test
    fun `second consume with same lines returns empty`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b"))
        tracker.consume(listOf("a", "b")) shouldBe ScrollUpdate(emptyList(), reset = false)
    }

    @Test
    fun `consume with appended lines returns only new lines`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a"))
        tracker.consume(listOf("a", "b", "c")) shouldBe ScrollUpdate(listOf("b", "c"), reset = false)
    }

    @Test
    fun `consume with replaced lines requests reset`() {
        val tracker = ScrollingContentTracker()
        tracker.consume(listOf("a", "b", "c"))
        tracker.consume(listOf("x", "y")) shouldBe ScrollUpdate(listOf("x", "y"), reset = true)
    }
}
