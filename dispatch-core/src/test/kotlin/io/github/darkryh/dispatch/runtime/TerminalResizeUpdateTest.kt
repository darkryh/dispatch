package io.github.darkryh.dispatch.runtime

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TerminalResizeUpdateTest {
    @Test
    fun `resize waits until signal burst settles`() {
        resizeHasSettled(
            lastSignalNanos = 1_000_000_000,
            nowNanos = 1_149_999_999,
            settleNanos = ResizeCoordinator.DEFAULT_RESIZE_SETTLE_NANOS,
        ) shouldBe false

        resizeHasSettled(
            lastSignalNanos = 1_000_000_000,
            nowNanos = 1_150_000_000,
            settleNanos = ResizeCoordinator.DEFAULT_RESIZE_SETTLE_NANOS,
        ) shouldBe true
    }

    @Test
    fun `no update when size is not dirty`() {
        val update =
            computeTerminalSizeUpdate(
                sizeDirty = false,
                previousWidth = 120,
                previousHeight = 24,
                currentWidth = 80,
                currentHeight = 20,
            )

        update shouldBe
            TerminalSizeUpdate(
                width = 120,
                height = 24,
                reset = false,
                dirty = false,
            )
    }

    @Test
    fun `dirty size with same dimensions does not reset`() {
        val update =
            computeTerminalSizeUpdate(
                sizeDirty = true,
                previousWidth = 120,
                previousHeight = 24,
                currentWidth = 120,
                currentHeight = 24,
            )

        update shouldBe
            TerminalSizeUpdate(
                width = 120,
                height = 24,
                reset = false,
                dirty = false,
            )
    }

    @Test
    fun `dirty size with change resets`() {
        val update =
            computeTerminalSizeUpdate(
                sizeDirty = true,
                previousWidth = 120,
                previousHeight = 24,
                currentWidth = 80,
                currentHeight = 20,
            )

        update shouldBe
            TerminalSizeUpdate(
                width = 80,
                height = 20,
                reset = true,
                dirty = false,
            )
    }

    @Test
    fun `viewport scrolling lines keeps only visible tail`() {
        val visible =
            viewportScrollingLines(
                scrollingLines = listOf("s1", "s2", "s3", "s4", "s5"),
                activeLines = listOf("a1", "a2"),
                terminalHeight = 5,
            )

        visible shouldBe listOf("s3", "s4", "s5")
    }

    @Test
    fun `overlay rewrite cannot replay more rows than viewport`() {
        val visible =
            viewportScrollingLines(
                scrollingLines = List(32) { "history-$it" },
                activeLines = List(12) { "overlay-$it" },
                terminalHeight = 30,
            )

        visible.size shouldBe 18
        visible.first() shouldBe "history-14"
        visible.last() shouldBe "history-31"
    }

    @Test
    fun `viewport scrolling lines empty when active area fills terminal`() {
        val visible =
            viewportScrollingLines(
                scrollingLines = listOf("s1", "s2"),
                activeLines = listOf("a1", "a2", "a3"),
                terminalHeight = 3,
            )

        visible shouldBe emptyList()
    }
}
