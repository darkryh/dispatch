package com.ead.dispatch.runtime.state

import com.ead.dispatch.state.derivedStateOf
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateListOf
import com.ead.dispatch.state.mutableStateMapOf
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.setValue
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class StateTest {

    @Test
    fun `mutableStateOf should hold initial value`() {
        val state = mutableStateOf(42)
        state.value shouldBe 42
    }

    @Test
    fun `mutableStateOf should update value`() {
        val state = mutableStateOf("hello")
        state.value = "world"
        state.value shouldBe "world"
    }

    @Test
    fun `mutableStateOf should support destructuring`() {
        val state = mutableStateOf(10)
        val (value, setValue) = state

        value shouldBe 10
        setValue(20)
        state.value shouldBe 20
    }

    @Test
    fun `mutableStateOf should support property delegation`() {
        var count by mutableStateOf(0)

        count shouldBe 0
        count++
        count shouldBe 1
        count = 100
        count shouldBe 100
    }

    @Test
    fun `State getValue should return current value`() {
        val state = mutableStateOf("test")
        val value by state

        value shouldBe "test"
    }

    @Test
    fun `derivedStateOf should compute value lazily`() {
        var computeCount = 0
        val source = mutableStateOf("initial")
        val derived = derivedStateOf {
            computeCount++
            source.value.uppercase()
        }

        // First access should compute
        derived.value shouldBe "INITIAL"
        computeCount shouldBe 1

        // Derived state caches until a dependency changes.
    }

    @Test
    fun `derivedStateOf updates when source changes`() {
        var source by mutableStateOf("initial")
        val derived = derivedStateOf { source.uppercase() }

        derived.value shouldBe "INITIAL"

        source = "next"

        derived.value shouldBe "NEXT"
    }

    @Test
    fun `subList mutations invalidate derived state`() {
        val list = mutableStateListOf("a", "b", "c")
        val derived = derivedStateOf { list.joinToString(",") }

        derived.value shouldBe "a,b,c"

        val sub = list.subList(0, 2)
        sub.removeAt(0)

        derived.value shouldBe "b,c"
    }

    @Test
    fun `map entry updates invalidate derived state`() {
        val map = mutableStateMapOf("a" to 1, "b" to 2)
        val derived = derivedStateOf { map["a"] }

        derived.value shouldBe 1

        val entry = map.entries.first { it.key == "a" }
        entry.setValue(3)

        derived.value shouldBe 3
    }

    @Test
    fun `map key removal invalidates derived state`() {
        val map = mutableStateMapOf("a" to 1, "b" to 2)
        val derived = derivedStateOf { map.size }

        derived.value shouldBe 2

        map.keys.remove("a")

        derived.value shouldBe 1
    }

    @Test
    fun `mutableStateOf should work with nullable types`() {
        val state = mutableStateOf<String?>(null)
        state.value shouldBe null

        state.value = "not null"
        state.value shouldBe "not null"

        state.value = null
        state.value shouldBe null
    }

    @Test
    fun `mutableStateOf should work with complex types`() {
        data class User(val name: String, val age: Int)

        val state = mutableStateOf(User("Alice", 30))
        state.value shouldBe User("Alice", 30)

        state.value = User("Bob", 25)
        state.value shouldBe User("Bob", 25)
    }

    @Test
    fun `mutableStateOf should work with collections`() {
        val state = mutableStateOf(listOf(1, 2, 3))
        state.value shouldBe listOf(1, 2, 3)

        state.value = listOf(4, 5, 6)
        state.value shouldBe listOf(4, 5, 6)
    }
}
