package com.ead.dispatch.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.ead.dispatch.runtime.SavedStateHandle

class ViewModelProviderTest {
    private class TestViewModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
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

            override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>): T {
                return TestViewModel() as T
            }

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
}
