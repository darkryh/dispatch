package com.ead.dispatch.state

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SnapshotFlowTest {

    @Test
    fun `snapshotFlow emits initial value and updates`() = runTest {
        val state = mutableStateOf(0)
        val emissions = mutableListOf<Int>()

        val job = launch {
            snapshotFlow { state.value }
                .take(3)
                .toList(emissions)
        }

        runCurrent()
        state.value = 1
        runCurrent()
        state.value = 2
        runCurrent()

        job.join()
        emissions shouldBe listOf(0, 1, 2)
    }

    @Test
    fun `snapshotFlow tracks multiple states`() = runTest {
        val a = mutableStateOf(1)
        val b = mutableStateOf(2)
        val emissions = mutableListOf<Int>()

        val job = launch {
            snapshotFlow { a.value + b.value }
                .take(3)
                .toList(emissions)
        }

        runCurrent()
        a.value = 2
        runCurrent()
        b.value = 3
        runCurrent()

        job.join()
        emissions shouldBe listOf(3, 4, 5)
    }

    @Test
    fun `snapshotFlow does not emit for equivalent updates`() = runTest {
        data class User(val name: String)

        val state = mutableStateOf(User("A"), structuralEqualityPolicy())
        val emissions = mutableListOf<User>()

        val job = launch {
            snapshotFlow { state.value }
                .take(2)
                .toList(emissions)
        }

        runCurrent()
        state.value = User("A")
        runCurrent()
        state.value = User("B")
        runCurrent()

        job.join()
        emissions shouldBe listOf(User("A"), User("B"))
    }
}
