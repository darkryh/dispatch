package com.ead.dispatch.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavControllerTest {
    @Test
    fun navigateAndPopUpdatesCurrentDestination() {
        val navController = NavController()

        navController.navigate("chat")
        navController.navigate("help")

        assertEquals("help", navController.currentDestination?.route)
        assertTrue(navController.canGoBack())

        assertTrue(navController.popBackStack())
        assertEquals("chat", navController.currentDestination?.route)
        assertFalse(navController.canGoBack())
    }

    @Test
    fun `popBackStack notifies listeners for popUpTo`() {
        val navController = NavController()
        val events = mutableListOf<NavigationEvent>()

        navController.addOnNavigationListener { event -> events.add(event) }

        navController.navigate("home")
        navController.navigate("detail")
        navController.navigate("settings")

        assertTrue(navController.popBackStack("home"))

        val popEvents = events.filterIsInstance<NavigationEvent.Pop>()
        assertEquals(listOf("settings", "detail"), popEvents.map { it.entry.route })
    }
}
