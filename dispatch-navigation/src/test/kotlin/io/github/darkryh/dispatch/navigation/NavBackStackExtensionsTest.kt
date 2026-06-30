package io.github.darkryh.dispatch.navigation

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class TestKey(
    val id: Int,
) : NavKey

class NavBackStackExtensionsTest {
    @Test
    fun navigateAndPopUpdatesStack() {
        val backStack = NavBackStack(TestKey(1))

        backStack.navigate(TestKey(2))

        assertEquals(2, backStack.size)
        assertTrue(backStack.canGoBack())

        assertTrue(backStack.popBackStack())
        assertEquals(1, backStack.size)
        assertFalse(backStack.popBackStack())
    }
}
