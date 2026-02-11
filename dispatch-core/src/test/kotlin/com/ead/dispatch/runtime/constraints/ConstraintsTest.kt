package com.ead.dispatch.runtime.constraints

import com.ead.dispatch.constraints.Constraints
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ConstraintsTest {
    @Test
    fun `default constraints should be unbounded`() {
        val constraints = Constraints()

        constraints.minWidth shouldBe 0
        constraints.maxWidth shouldBe Int.MAX_VALUE
        constraints.minHeight shouldBe 0
        constraints.maxHeight shouldBe Int.MAX_VALUE
    }

    @Test
    fun `hasBoundedWidth should return true when maxWidth is not MAX_VALUE`() {
        val bounded = Constraints(maxWidth = 100)
        val unbounded = Constraints()

        bounded.hasBoundedWidth shouldBe true
        unbounded.hasBoundedWidth shouldBe false
    }

    @Test
    fun `hasBoundedHeight should return true when maxHeight is not MAX_VALUE`() {
        val bounded = Constraints(maxHeight = 50)
        val unbounded = Constraints()

        bounded.hasBoundedHeight shouldBe true
        unbounded.hasBoundedHeight shouldBe false
    }

    @Test
    fun `hasFixedWidth should return true when min equals max`() {
        val fixed = Constraints(minWidth = 50, maxWidth = 50)
        val variable = Constraints(minWidth = 10, maxWidth = 100)

        fixed.hasFixedWidth shouldBe true
        variable.hasFixedWidth shouldBe false
    }

    @Test
    fun `hasFixedHeight should return true when min equals max`() {
        val fixed = Constraints(minHeight = 20, maxHeight = 20)
        val variable = Constraints(minHeight = 5, maxHeight = 30)

        fixed.hasFixedHeight shouldBe true
        variable.hasFixedHeight shouldBe false
    }

    @Test
    fun `isFixed should return true when both dimensions are fixed`() {
        val fixed = Constraints(minWidth = 50, maxWidth = 50, minHeight = 20, maxHeight = 20)
        val partiallyFixed = Constraints(minWidth = 50, maxWidth = 50, minHeight = 10, maxHeight = 30)

        fixed.isFixed shouldBe true
        partiallyFixed.isFixed shouldBe false
    }

    @Test
    fun `constrainWidth should clamp value to range`() {
        val constraints = Constraints(minWidth = 10, maxWidth = 100)

        constraints.constrainWidth(5) shouldBe 10
        constraints.constrainWidth(50) shouldBe 50
        constraints.constrainWidth(150) shouldBe 100
    }

    @Test
    fun `constrainHeight should clamp value to range`() {
        val constraints = Constraints(minHeight = 5, maxHeight = 50)

        constraints.constrainHeight(2) shouldBe 5
        constraints.constrainHeight(25) shouldBe 25
        constraints.constrainHeight(100) shouldBe 50
    }

    @Test
    fun `constrain should clamp both dimensions`() {
        val constraints =
            Constraints(
                minWidth = 10,
                maxWidth = 100,
                minHeight = 5,
                maxHeight = 50,
            )

        val (w, h) = constraints.constrain(5, 2)
        w shouldBe 10
        h shouldBe 5

        val (w2, h2) = constraints.constrain(50, 25)
        w2 shouldBe 50
        h2 shouldBe 25

        val (w3, h3) = constraints.constrain(150, 100)
        w3 shouldBe 100
        h3 shouldBe 50
    }

    @Test
    fun `Unbounded factory should create unbounded constraints`() {
        val constraints = Constraints.Unbounded

        constraints.minWidth shouldBe 0
        constraints.maxWidth shouldBe Int.MAX_VALUE
        constraints.minHeight shouldBe 0
        constraints.maxHeight shouldBe Int.MAX_VALUE
    }

    @Test
    fun `Zero factory should create zero-sized constraints`() {
        val constraints = Constraints.Zero

        constraints.minWidth shouldBe 0
        constraints.maxWidth shouldBe 0
        constraints.minHeight shouldBe 0
        constraints.maxHeight shouldBe 0
    }

    @Test
    fun `fixed factory should create fixed constraints`() {
        val constraints = Constraints.fixed(80, 24)

        constraints.minWidth shouldBe 80
        constraints.maxWidth shouldBe 80
        constraints.minHeight shouldBe 24
        constraints.maxHeight shouldBe 24
        constraints.isFixed shouldBe true
    }

    @Test
    fun `fixedWidth factory should create width-fixed constraints`() {
        val constraints = Constraints.fixedWidth(80)

        constraints.minWidth shouldBe 80
        constraints.maxWidth shouldBe 80
        constraints.hasFixedWidth shouldBe true
        constraints.hasFixedHeight shouldBe false
    }

    @Test
    fun `fixedHeight factory should create height-fixed constraints`() {
        val constraints = Constraints.fixedHeight(24)

        constraints.minHeight shouldBe 24
        constraints.maxHeight shouldBe 24
        constraints.hasFixedWidth shouldBe false
        constraints.hasFixedHeight shouldBe true
    }

    @Test
    fun `maxSize factory should create max-bounded constraints`() {
        val constraints = Constraints.maxSize(100, 50)

        constraints.minWidth shouldBe 0
        constraints.maxWidth shouldBe 100
        constraints.minHeight shouldBe 0
        constraints.maxHeight shouldBe 50
    }

    @Test
    fun `minSize factory should create min-bounded constraints`() {
        val constraints = Constraints.minSize(10, 5)

        constraints.minWidth shouldBe 10
        constraints.maxWidth shouldBe Int.MAX_VALUE
        constraints.minHeight shouldBe 5
        constraints.maxHeight shouldBe Int.MAX_VALUE
    }

    @Test
    fun `offset should adjust constraints by subtracting`() {
        val constraints = Constraints(minWidth = 20, maxWidth = 100, minHeight = 10, maxHeight = 50)
        val offset = constraints.offset(5, 2) // Subtracts from constraints

        offset.minWidth shouldBe 15
        offset.maxWidth shouldBe 95
        offset.minHeight shouldBe 8
        offset.maxHeight shouldBe 48
    }

    @Test
    fun `offset should not go below zero`() {
        val constraints = Constraints(minWidth = 5, maxWidth = 10, minHeight = 3, maxHeight = 8)
        val offset = constraints.offset(20, 20) // Large offset subtracts to zero

        offset.minWidth shouldBe 0
        offset.maxWidth shouldBe 0
        offset.minHeight shouldBe 0
        offset.maxHeight shouldBe 0
    }

    @Test
    fun `isZero should return true for zero constraints`() {
        Constraints.Zero.isZero shouldBe true
        Constraints(minWidth = 0, maxWidth = 0, minHeight = 0, maxHeight = 0).isZero shouldBe true
        Constraints(maxWidth = 1).isZero shouldBe false
    }
}
