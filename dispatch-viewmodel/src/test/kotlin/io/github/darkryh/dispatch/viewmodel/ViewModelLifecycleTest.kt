package io.github.darkryh.dispatch.viewmodel

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import kotlin.test.Test
import kotlin.test.assertTrue

class ViewModelLifecycleTest {
    private class TestViewModel : ViewModel()

    private class CloseableProbe : Closeable {
        var closed = false

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `addCloseable closes when viewmodel is cleared`() {
        val viewModel = TestViewModel()
        val closeable = CloseableProbe()

        viewModel.addCloseable(closeable)
        viewModel.clear()

        assertTrue(closeable.closed)
    }

    @Test
    fun `addCloseable closes immediately when already cleared`() {
        val viewModel = TestViewModel()
        viewModel.clear()

        val closeable = CloseableProbe()
        viewModel.addCloseable(closeable)

        assertTrue(closeable.closed)
    }

    @Test
    fun `clear cancels viewmodel scope`() =
        runBlocking {
            val viewModel = TestViewModel()
            val job =
                viewModel.viewModelScope.launch {
                    delay(10_000)
                }

            viewModel.clear()

            withTimeout(1_000) {
                job.join()
            }

            assertTrue(job.isCancelled)
        }
}
