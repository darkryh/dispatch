package com.ead.dispatch.runtime.runtime

import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.compositionLocalOf
import com.ead.dispatch.runtime.staticCompositionLocalOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CompositionLocalTest {
    @Test
    fun `compositionLocalOf should return default value when not provided`() {
        val local = compositionLocalOf { "default" }
        local.current shouldBe "default"
    }

    @Test
    fun `compositionLocalOf should throw when no default and not provided`() {
        val local = compositionLocalOf<String> { error("Not provided") }

        shouldThrow<IllegalStateException> {
            local.current
        }
    }

    @Test
    fun `CompositionLocalProvider should provide value to content`() {
        val local = compositionLocalOf { "default" }

        CompositionLocalProvider(local provides "custom") {
            local.current shouldBe "custom"
        }

        // After provider, should return to default
        local.current shouldBe "default"
    }

    @Test
    fun `nested CompositionLocalProviders should work correctly`() {
        val local = compositionLocalOf { "default" }

        CompositionLocalProvider(local provides "outer") {
            local.current shouldBe "outer"

            CompositionLocalProvider(local provides "inner") {
                local.current shouldBe "inner"
            }

            local.current shouldBe "outer"
        }

        local.current shouldBe "default"
    }

    @Test
    fun `multiple CompositionLocals can be provided at once`() {
        val localA = compositionLocalOf { "defaultA" }
        val localB = compositionLocalOf { 0 }

        CompositionLocalProvider(
            localA provides "valueA",
            localB provides 42,
        ) {
            localA.current shouldBe "valueA"
            localB.current shouldBe 42
        }

        localA.current shouldBe "defaultA"
        localB.current shouldBe 0
    }

    @Test
    fun `provides should create ProvidedValue`() {
        val local = compositionLocalOf { "default" }
        val provided = local provides "custom"

        provided.compositionLocal shouldBe local
        provided.value shouldBe "custom"
    }

    @Test
    fun `staticCompositionLocalOf should behave like compositionLocalOf`() {
        val local = staticCompositionLocalOf { 100 }
        local.current shouldBe 100

        CompositionLocalProvider(local provides 200) {
            local.current shouldBe 200
        }

        local.current shouldBe 100
    }

    @Test
    fun `CompositionLocalProvider should handle nullable values`() {
        val local = compositionLocalOf<String?> { null }

        local.current shouldBe null

        CompositionLocalProvider(local provides "not null") {
            local.current shouldBe "not null"
        }

        local.current shouldBe null
    }

    @Test
    fun `CompositionLocalProvider should restore explicit null values`() {
        val local = compositionLocalOf<String?> { "default" }

        CompositionLocalProvider(local provides null) {
            local.current shouldBe null

            CompositionLocalProvider(local provides "inner") {
                local.current shouldBe "inner"
            }

            local.current shouldBe null
        }

        local.current shouldBe "default"
    }

    @Test
    fun `CompositionLocalProvider should restore previous value on exception`() {
        val local = compositionLocalOf { "default" }

        shouldThrow<IllegalStateException> {
            CompositionLocalProvider(local provides "custom") {
                local.current shouldBe "custom"
                error("Test exception")
            }
        }

        local.current shouldBe "default"
    }
}
