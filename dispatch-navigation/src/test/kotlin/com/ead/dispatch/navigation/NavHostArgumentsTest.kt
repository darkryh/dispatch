package com.ead.dispatch.navigation

import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.withComposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class NavHostArgumentsTest {
    private fun renderHost(
        navController: NavController,
        startDestination: String,
        builder: NavGraphBuilder.() -> Unit,
    ) {
        val composer = Composer()
        withComposer(composer) {
            composer.startComposition()
            try {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    builder = builder,
                )
            } finally {
                composer.endComposition()
            }
        }
    }

    @Test
    fun `nav host maps path arguments to names`() {
        val navController = NavController()

        renderHost(navController, "detail/123") {
            screen("detail/{id}") { }
        }

        val entry = navController.currentDestination
        assertNotNull(entry)
        assertEquals("123", entry.arguments["id"])
        assertEquals("123", entry.savedStateHandle.get<String>("id"))
    }

    @Test
    fun `nav host applies default arguments`() {
        val navController = NavController()

        renderHost(navController, "list") {
            screen(
                route = "list",
                arguments = listOf(intArg("page", optional = true, default = 1)),
            ) { }
        }

        val entry = navController.currentDestination
        assertNotNull(entry)
        assertEquals("1", entry.arguments["page"])
    }

    @Test
    fun `nav host throws when required argument is missing`() {
        val navController = NavController()

        assertFailsWith<IllegalArgumentException> {
            renderHost(navController, "list") {
                screen(
                    route = "list",
                    arguments = listOf(intArg("page")),
                ) { }
            }
        }
    }
}
