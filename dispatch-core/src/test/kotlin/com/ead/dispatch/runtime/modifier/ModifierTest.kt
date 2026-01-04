package com.ead.dispatch.runtime.modifier

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.PaddingModifier
import com.ead.dispatch.modifier.SizeModifier
import com.ead.dispatch.modifier.WeightModifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.modifier.fillMaxHeight
import com.ead.dispatch.modifier.fillMaxSize
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.heightIn
import com.ead.dispatch.modifier.horizontalPadding
import com.ead.dispatch.modifier.padding
import com.ead.dispatch.modifier.size
import com.ead.dispatch.modifier.verticalPadding
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.modifier.width
import com.ead.dispatch.modifier.widthIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class ModifierTest {

    @Test
    fun `Modifier companion should be empty modifier`() {
        val modifier = Modifier
        modifier.foldIn(0) { acc, _ -> acc + 1 } shouldBe 0
    }

    @Test
    fun `then should chain modifiers`() {
        val modifier = Modifier
            .width(10)
            .height(5)

        var count = 0
        modifier.foldIn(Unit) { _, _ -> count++ }
        count shouldBe 2
    }

    @Test
    fun `width modifier should set fixed width`() {
        val modifier = Modifier.width(50)
        val constraints = Constraints(maxWidth = 100, maxHeight = 100)
        val result = modifier.applyToConstraints(constraints)

        result.minWidth shouldBe 50
        result.maxWidth shouldBe 50
    }

    @Test
    fun `height modifier should set fixed height`() {
        val modifier = Modifier.height(20)
        val constraints = Constraints(maxWidth = 100, maxHeight = 100)
        val result = modifier.applyToConstraints(constraints)

        result.minHeight shouldBe 20
        result.maxHeight shouldBe 20
    }

    @Test
    fun `size modifier should set both dimensions`() {
        val modifier = Modifier.size(30, 15)
        val constraints = Constraints(maxWidth = 100, maxHeight = 100)
        val result = modifier.applyToConstraints(constraints)

        result.minWidth shouldBe 30
        result.maxWidth shouldBe 30
        result.minHeight shouldBe 15
        result.maxHeight shouldBe 15
    }

    @Test
    fun `fillMaxWidth should set width to max constraint`() {
        val modifier = Modifier.fillMaxWidth()
        val constraints = Constraints(maxWidth = 80, maxHeight = 24)
        val result = modifier.applyToConstraints(constraints)

        result.minWidth shouldBe 80
        result.maxWidth shouldBe 80
    }

    @Test
    fun `fillMaxWidth with fraction should set proportional width`() {
        val modifier = Modifier.fillMaxWidth(0.5f)
        val constraints = Constraints(maxWidth = 100, maxHeight = 24)
        val result = modifier.applyToConstraints(constraints)

        result.minWidth shouldBe 50
        result.maxWidth shouldBe 50
    }

    @Test
    fun `fillMaxHeight should set height to max constraint`() {
        val modifier = Modifier.fillMaxHeight()
        val constraints = Constraints(maxWidth = 80, maxHeight = 24)
        val result = modifier.applyToConstraints(constraints)

        result.minHeight shouldBe 24
        result.maxHeight shouldBe 24
    }

    @Test
    fun `fillMaxSize should set both dimensions to max`() {
        val modifier = Modifier.fillMaxSize()
        val constraints = Constraints(maxWidth = 80, maxHeight = 24)
        val result = modifier.applyToConstraints(constraints)

        result.minWidth shouldBe 80
        result.maxWidth shouldBe 80
        result.minHeight shouldBe 24
        result.maxHeight shouldBe 24
    }

    @Test
    fun `widthIn should set min and max width bounds`() {
        val modifier = Modifier.widthIn(min = 10, max = 50)
        val constraints = Constraints(maxWidth = 100, maxHeight = 100)
        val result = modifier.applyToConstraints(constraints)

        result.minWidth shouldBe 10
        result.maxWidth shouldBe 50
    }

    @Test
    fun `heightIn should set min and max height bounds`() {
        val modifier = Modifier.heightIn(min = 5, max = 20)
        val constraints = Constraints(maxWidth = 100, maxHeight = 100)
        val result = modifier.applyToConstraints(constraints)

        result.minHeight shouldBe 5
        result.maxHeight shouldBe 20
    }

    @Test
    fun `padding modifier should be accessible`() {
        val modifier = Modifier.padding(2)
        val paddingMod = modifier.firstOrNull(PaddingModifier::class.java)

        paddingMod.shouldBeInstanceOf<PaddingModifier>()
        paddingMod.start shouldBe 2
        paddingMod.end shouldBe 2
        paddingMod.top shouldBe 2
        paddingMod.bottom shouldBe 2
    }

    @Test
    fun `padding with different values should work`() {
        val modifier = Modifier.padding(start = 1, end = 2, top = 3, bottom = 4)
        val paddingMod = modifier.firstOrNull(PaddingModifier::class.java)!!

        paddingMod.start shouldBe 1
        paddingMod.end shouldBe 2
        paddingMod.top shouldBe 3
        paddingMod.bottom shouldBe 4
    }

    @Test
    fun `horizontalPadding should set start and end`() {
        val modifier = Modifier.horizontalPadding(5)
        val paddingMod = modifier.firstOrNull(PaddingModifier::class.java)!!

        paddingMod.start shouldBe 5
        paddingMod.end shouldBe 5
        paddingMod.top shouldBe 0
        paddingMod.bottom shouldBe 0
    }

    @Test
    fun `verticalPadding should set top and bottom`() {
        val modifier = Modifier.verticalPadding(3)
        val paddingMod = modifier.firstOrNull(PaddingModifier::class.java)!!

        paddingMod.start shouldBe 0
        paddingMod.end shouldBe 0
        paddingMod.top shouldBe 3
        paddingMod.bottom shouldBe 3
    }

    @Test
    fun `weight modifier should be accessible`() {
        val modifier = Modifier.weight(2.0f)
        val weightMod = modifier.firstOrNull(WeightModifier::class.java)

        weightMod.shouldBeInstanceOf<WeightModifier>()
        weightMod.weight shouldBe 2.0f
    }

    @Test
    fun `chained modifiers should all be applied`() {
        val modifier = Modifier
            .width(50)
            .height(10)
            .padding(2)

        val elements = mutableListOf<Modifier.Element>()
        modifier.foldIn(Unit) { _, element -> elements.add(element) }

        elements.size shouldBe 3
    }

    @Test
    fun `firstOrNull should return first matching element`() {
        val modifier = Modifier
            .width(50)
            .padding(2)
            .height(10)

        val sizeMod = modifier.firstOrNull(SizeModifier::class.java)
        sizeMod.shouldBeInstanceOf<SizeModifier>()
    }

    @Test
    fun `allOf should return all matching elements`() {
        val modifier = Modifier
            .width(50)
            .padding(2)
            .height(10)

        val sizeModifiers = modifier.allOf(SizeModifier::class.java)
        sizeModifiers.size shouldBe 2
    }
}
