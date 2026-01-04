package com.ead.dispatch.runtime.viewmodel

import com.ead.dispatch.viewmodel.LoadingState
import com.ead.dispatch.viewmodel.Resource
import com.ead.dispatch.viewmodel.StateViewModel
import com.ead.dispatch.viewmodel.ViewModel
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ViewModelTest {

    @Test
    fun `ViewModel should have active coroutine scope`() {
        val viewModel = TestViewModel()
        // Scope is active by default
        viewModel.isCleared shouldBe false
    }

    @Test
    fun `ViewModel clear should set isCleared`() {
        val viewModel = TestViewModel()
        viewModel.clear()

        viewModel.isCleared shouldBe true
    }

    @Test
    fun `ViewModel onCleared should be called on clear`() {
        val viewModel = TestViewModel()
        viewModel.clear()

        viewModel.onClearedCalled shouldBe true
    }

    @Test
    fun `ViewModel close should call clear`() {
        val viewModel = TestViewModel()
        viewModel.close()

        viewModel.isCleared shouldBe true
    }

    @Test
    fun `ViewModel clear should be idempotent`() {
        val viewModel = TestViewModel()

        viewModel.clear()
        viewModel.clear()  // Second call should not throw

        viewModel.onClearedCalled shouldBe true
        viewModel.isCleared shouldBe true
    }

    @Test
    fun `StateViewModel should hold initial state`() = runTest {
        val viewModel = TestStateViewModel("initial")
        viewModel.state.value shouldBe "initial"
        viewModel.currentState shouldBe "initial"
    }

    @Test
    fun `StateViewModel updateState should update state`() = runTest {
        val viewModel = TestStateViewModel("initial")

        viewModel.testUpdateState { "$it updated" }
        viewModel.state.value shouldBe "initial updated"
        viewModel.currentState shouldBe "initial updated"
    }

    @Test
    fun `StateViewModel setState should replace state`() = runTest {
        val viewModel = TestStateViewModel("initial")

        viewModel.testSetState("new state")
        viewModel.state.value shouldBe "new state"
        viewModel.currentState shouldBe "new state"
    }

    @Test
    fun `LoadingState Loading should have correct properties`() {
        val state: LoadingState<String> = LoadingState.Loading

        state.isLoading shouldBe true
        state.isSuccess shouldBe false
        state.isError shouldBe false
        state.getOrNull() shouldBe null
        state.getOrDefault("default") shouldBe "default"
    }

    @Test
    fun `LoadingState Success should have correct properties`() {
        val state: LoadingState<String> = LoadingState.Success("data")

        state.isLoading shouldBe false
        state.isSuccess shouldBe true
        state.isError shouldBe false
        state.getOrNull() shouldBe "data"
        state.getOrDefault("default") shouldBe "data"
    }

    @Test
    fun `LoadingState Error should have correct properties`() {
        val state: LoadingState<String> = LoadingState.Error("error message")

        state.isLoading shouldBe false
        state.isSuccess shouldBe false
        state.isError shouldBe true
        state.getOrNull() shouldBe null
        state.getOrDefault("default") shouldBe "default"
    }

    @Test
    fun `LoadingState Error with cause should have cause`() {
        val cause = RuntimeException("test")
        val state: LoadingState<String> = LoadingState.Error("error", cause)

        (state as LoadingState.Error).cause shouldBe cause
    }

    @Test
    fun `Resource Idle should have correct properties`() {
        val state: Resource<String> = Resource.Idle

        state.isIdle shouldBe true
        state.isLoading shouldBe false
        state.isSuccess shouldBe false
        state.isError shouldBe false
        state.getOrNull() shouldBe null
    }

    @Test
    fun `Resource Loading should have correct properties`() {
        val state: Resource<String> = Resource.Loading

        state.isIdle shouldBe false
        state.isLoading shouldBe true
        state.isSuccess shouldBe false
        state.isError shouldBe false
        state.getOrNull() shouldBe null
    }

    @Test
    fun `Resource Success should have correct properties`() {
        val state: Resource<String> = Resource.Success("data")

        state.isIdle shouldBe false
        state.isLoading shouldBe false
        state.isSuccess shouldBe true
        state.isError shouldBe false
        state.getOrNull() shouldBe "data"
    }

    @Test
    fun `Resource Error should have correct properties`() {
        val state: Resource<String> = Resource.Error("error message")

        state.isIdle shouldBe false
        state.isLoading shouldBe false
        state.isSuccess shouldBe false
        state.isError shouldBe true
        state.getOrNull() shouldBe null
    }

    // Test implementations

    private class TestViewModel : ViewModel() {
        var onClearedCalled = false

        override fun onCleared() {
            onClearedCalled = true
        }
    }

    private class TestStateViewModel(initial: String) : StateViewModel<String>(initial) {
        fun testUpdateState(transform: (String) -> String) = updateState(transform)
        fun testSetState(state: String) = setState(state)
    }
}
