package com.ead.dispatch.runtime.layout

import com.ead.dispatch.layout.Arrangement
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ArrangementTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Vertical Arrangements
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Top arrangement should start items at 0`() {
        val sizes = listOf(10, 20, 15)
        val positions = Arrangement.Top.arrange(100, sizes)

        positions shouldBe listOf(0, 10, 30)
    }

    @Test
    fun `Bottom arrangement should end items at bottom`() {
        val sizes = listOf(10, 20, 15)
        val positions = Arrangement.Bottom.arrange(100, sizes)

        // Total content = 45, starts at 55
        positions shouldBe listOf(55, 65, 85)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Horizontal Arrangements
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Start arrangement should start items at 0`() {
        val sizes = listOf(10, 20, 15)
        val positions = Arrangement.Start.arrange(100, sizes)

        positions shouldBe listOf(0, 10, 30)
    }

    @Test
    fun `End arrangement should end items at end`() {
        val sizes = listOf(10, 20, 15)
        val positions = Arrangement.End.arrange(100, sizes)

        // Total content = 45, starts at 55
        positions shouldBe listOf(55, 65, 85)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Center Arrangement
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Center arrangement should center items`() {
        val sizes = listOf(10, 20, 10)
        val positions = Arrangement.Center.arrange(100, sizes)

        // Total content = 40, offset = 30
        positions shouldBe listOf(30, 40, 60)
    }

    @Test
    fun `Center arrangement should handle odd offsets`() {
        val sizes = listOf(10, 10, 10)
        val positions = Arrangement.Center.arrange(100, sizes)

        // Total content = 30, offset = 35
        positions shouldBe listOf(35, 45, 55)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SpaceBetween Arrangement
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `SpaceBetween should distribute items evenly with gaps`() {
        val sizes = listOf(10, 10, 10)
        val positions = Arrangement.SpaceBetween.arrange(100, sizes)

        // Total content = 30, space = 70, gaps = 2, gap size = 35
        positions shouldBe listOf(0, 45, 90)
    }

    @Test
    fun `SpaceBetween should handle single item`() {
        val sizes = listOf(20)
        val positions = Arrangement.SpaceBetween.arrange(100, sizes)

        positions shouldBe listOf(0)
    }

    @Test
    fun `SpaceBetween should handle two items`() {
        val sizes = listOf(20, 20)
        val positions = Arrangement.SpaceBetween.arrange(100, sizes)

        positions shouldBe listOf(0, 80)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SpaceAround Arrangement
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `SpaceAround should add equal space around items`() {
        val sizes = listOf(10, 10, 10)
        val positions = Arrangement.SpaceAround.arrange(100, sizes)

        // Total content = 30, space = 70, partitions = 6 (3 items * 2)
        // Space unit = 70/6 ≈ 11
        // First item at 11, second at 11 + 10 + 22, etc.
        positions[0] shouldBe 11
    }

    @Test
    fun `SpaceAround should handle single item`() {
        val sizes = listOf(20)
        val positions = Arrangement.SpaceAround.arrange(100, sizes)

        // Centered
        positions shouldBe listOf(40)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SpaceEvenly Arrangement
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `SpaceEvenly should distribute space equally between all gaps`() {
        val sizes = listOf(10, 10, 10)
        val positions = Arrangement.SpaceEvenly.arrange(100, sizes)

        // Total content = 30, space = 70, gaps = 4 (items + 1), gap = 17
        positions shouldBe listOf(17, 44, 71)
    }

    @Test
    fun `SpaceEvenly should handle single item`() {
        val sizes = listOf(20)
        val positions = Arrangement.SpaceEvenly.arrange(100, sizes)

        // Centered
        positions shouldBe listOf(40)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SpacedBy Arrangement
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `spacedBy should add fixed spacing between items`() {
        val arrangement = Arrangement.spacedBy(5)
        val sizes = listOf(10, 20, 15)
        val positions = arrangement.arrange(100, sizes)

        positions shouldBe listOf(0, 15, 40)
    }

    @Test
    fun `spacedBy should handle single item`() {
        val arrangement = Arrangement.spacedBy(10)
        val sizes = listOf(20)
        val positions = arrangement.arrange(100, sizes)

        positions shouldBe listOf(0)
    }

    @Test
    fun `spacedBy with zero spacing should act like Start`() {
        val arrangement = Arrangement.spacedBy(0)
        val sizes = listOf(10, 20, 15)
        val positions = arrangement.arrange(100, sizes)

        positions shouldBe listOf(0, 10, 30)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `arrangements should handle empty list`() {
        Arrangement.Start.arrange(100, emptyList()) shouldBe emptyList()
        Arrangement.Center.arrange(100, emptyList()) shouldBe emptyList()
        Arrangement.SpaceBetween.arrange(100, emptyList()) shouldBe emptyList()
    }

    @Test
    fun `arrangements should handle content larger than container`() {
        val sizes = listOf(50, 50, 50)
        val positions = Arrangement.Start.arrange(100, sizes)

        // Items positioned starting at 0, even if they overflow
        positions shouldBe listOf(0, 50, 100)
    }

    @Test
    fun `arrangements should handle zero container size`() {
        val sizes = listOf(10, 10)
        val positions = Arrangement.Start.arrange(0, sizes)

        positions shouldBe listOf(0, 10)
    }
}
