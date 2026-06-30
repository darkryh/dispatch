package io.github.darkryh.dispatch.runtime.layout

import io.github.darkryh.dispatch.layout.Alignment
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AlignmentTest {
    // ═══════════════════════════════════════════════════════════════════════════
    // Horizontal Alignment
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Start alignment should position at 0`() {
        Alignment.Start.align(100, 20) shouldBe 0
    }

    @Test
    fun `End alignment should position at container minus content`() {
        Alignment.End.align(100, 20) shouldBe 80
    }

    @Test
    fun `CenterHorizontally should center content`() {
        Alignment.CenterHorizontally.align(100, 20) shouldBe 40
    }

    @Test
    fun `CenterHorizontally should handle odd differences`() {
        Alignment.CenterHorizontally.align(100, 21) shouldBe 39
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Vertical Alignment
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Top alignment should position at 0`() {
        Alignment.Top.align(50, 10) shouldBe 0
    }

    @Test
    fun `Bottom alignment should position at container minus content`() {
        Alignment.Bottom.align(50, 10) shouldBe 40
    }

    @Test
    fun `CenterVertically should center content`() {
        Alignment.CenterVertically.align(50, 10) shouldBe 20
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2D Alignment
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `TopStart should position at top-left`() {
        Alignment.TopStart.horizontal shouldBe Alignment.Start
        Alignment.TopStart.vertical shouldBe Alignment.Top
    }

    @Test
    fun `TopCenter should position at top-center`() {
        Alignment.TopCenter.horizontal shouldBe Alignment.CenterHorizontally
        Alignment.TopCenter.vertical shouldBe Alignment.Top
    }

    @Test
    fun `TopEnd should position at top-right`() {
        Alignment.TopEnd.horizontal shouldBe Alignment.End
        Alignment.TopEnd.vertical shouldBe Alignment.Top
    }

    @Test
    fun `CenterStart should position at center-left`() {
        Alignment.CenterStart.horizontal shouldBe Alignment.Start
        Alignment.CenterStart.vertical shouldBe Alignment.CenterVertically
    }

    @Test
    fun `Center should position at center`() {
        Alignment.Center.horizontal shouldBe Alignment.CenterHorizontally
        Alignment.Center.vertical shouldBe Alignment.CenterVertically
    }

    @Test
    fun `CenterEnd should position at center-right`() {
        Alignment.CenterEnd.horizontal shouldBe Alignment.End
        Alignment.CenterEnd.vertical shouldBe Alignment.CenterVertically
    }

    @Test
    fun `BottomStart should position at bottom-left`() {
        Alignment.BottomStart.horizontal shouldBe Alignment.Start
        Alignment.BottomStart.vertical shouldBe Alignment.Bottom
    }

    @Test
    fun `BottomCenter should position at bottom-center`() {
        Alignment.BottomCenter.horizontal shouldBe Alignment.CenterHorizontally
        Alignment.BottomCenter.vertical shouldBe Alignment.Bottom
    }

    @Test
    fun `BottomEnd should position at bottom-right`() {
        Alignment.BottomEnd.horizontal shouldBe Alignment.End
        Alignment.BottomEnd.vertical shouldBe Alignment.Bottom
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `alignment should handle zero container size`() {
        Alignment.Start.align(0, 0) shouldBe 0
        Alignment.CenterHorizontally.align(0, 0) shouldBe 0
        Alignment.End.align(0, 0) shouldBe 0
    }

    @Test
    fun `alignment should handle content larger than container`() {
        // When content is larger, alignments coerce to 0
        Alignment.End.align(10, 20) shouldBe 0
        Alignment.CenterHorizontally.align(10, 20) shouldBe 0
    }

    @Test
    fun `alignment should handle same size content and container`() {
        Alignment.Start.align(50, 50) shouldBe 0
        Alignment.End.align(50, 50) shouldBe 0
        Alignment.CenterHorizontally.align(50, 50) shouldBe 0
    }
}
