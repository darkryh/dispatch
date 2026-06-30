package io.github.darkryh.dispatch.viewmodel

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ViewModelStoreTest {
    private class TestViewModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    @BeforeTest
    fun setUp() {
        ViewModelStore.clear()
    }

    @AfterTest
    fun tearDown() {
        ViewModelStore.clear()
    }

    @Test
    fun `store caches instances by key`() {
        val first = ViewModelStore.getOrCreate("alpha") { TestViewModel() }
        val second = ViewModelStore.getOrCreate("alpha") { TestViewModel() }

        assertSame(first, second)
    }

    @Test
    fun `store returns new instance after clear`() {
        val first = ViewModelStore.getOrCreate("alpha") { TestViewModel() }

        ViewModelStore.clear()

        val second = ViewModelStore.getOrCreate("alpha") { TestViewModel() }

        assertTrue(first.cleared)
        assertNotSame(first, second)
    }
}
