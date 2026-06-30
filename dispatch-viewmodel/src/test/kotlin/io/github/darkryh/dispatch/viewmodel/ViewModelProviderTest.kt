package io.github.darkryh.dispatch.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.github.darkryh.dispatch.runtime.SavedStateHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class ViewModelProviderTest {
    private class TestViewModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private class OtherViewModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private class ScopedViewModel : ViewModel() {
        val job: Job = viewModelScope.launch { delay(10.seconds) }
    }

    @Test
    fun `provider caches viewmodels by key`() {
        val provider = ViewModelProvider()

        val first = provider.get(TestViewModel::class, "alpha")
        val second = provider.get(TestViewModel::class, "alpha")

        assertSame(first, second)
    }

    @Test
    fun `provider returns different instances for different keys`() {
        val provider = ViewModelProvider()

        val first = provider.get(TestViewModel::class, "alpha")
        val second = provider.get(TestViewModel::class, "beta")

        assertNotSame(first, second)
    }

    @Test
    fun `provider clear removes instance and calls onCleared`() {
        val provider = ViewModelProvider()

        val first = provider.get(TestViewModel::class, "alpha")
        provider.clear("alpha")

        assertTrue(first.cleared)

        val second = provider.get(TestViewModel::class, "alpha")
        assertNotSame(first, second)
    }

    @Test
    fun `provider clear all clears every model`() {
        val provider = ViewModelProvider()

        val first = provider.get(TestViewModel::class, "alpha")
        val second = provider.get(TestViewModel::class, "beta")

        provider.clear()

        assertTrue(first.cleared)
        assertTrue(second.cleared)
    }

    @Test
    fun `custom factory supplies instances`() {
        val factory = viewModelFactory {
            add { TestViewModel() }
        }
        val provider = ViewModelProvider(factory)

        val model = provider.get(TestViewModel::class)

        assertEquals(TestViewModel::class, model::class)
    }

    @Test
    fun `provider uses saved state factory when handle is provided`() {
        val handle = SavedStateHandle().apply { this["key"] = "value" }
        val factory = object : SavedStateViewModelFactory {
            var lastHandle: SavedStateHandle? = null

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>): T = TestViewModel() as T

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: kotlin.reflect.KClass<T>,
                savedStateHandle: SavedStateHandle,
            ): T {
                lastHandle = savedStateHandle
                return TestViewModel() as T
            }
        }

        val provider = ViewModelProvider(factory, handle)
        provider.get(TestViewModel::class)

        assertSame(handle, factory.lastHandle)
    }

    @Test
    fun `clear cancels all child viewmodel scopes`() = runBlocking {
        val provider = ViewModelProvider()
        val first = provider.get(ScopedViewModel::class, "a")
        val second = provider.get(ScopedViewModel::class, "b")

        provider.clear()

        assertTrue(first.isCleared)
        assertTrue(second.isCleared)
        withTimeout(1.seconds) {
            first.job.join()
            second.job.join()
        }
        assertTrue(first.job.isCancelled)
        assertTrue(second.job.isCancelled)
    }

    @Test
    fun `type mismatch on same key clears displaced instance`() {
        val provider = ViewModelProvider()

        val first = provider.get(TestViewModel::class, "shared")
        // Request a different type under the same key -> displaces first.
        val second = provider.get(OtherViewModel::class, "shared")

        assertTrue(first.cleared, "displaced instance must be cleared")
        assertFalse(second.cleared)
        assertNotSame<Any>(first, second)
    }
}
