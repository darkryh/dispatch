package com.ead.dispatch.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
@SerialName("demo")
private data class DemoRoute(val value: String)

class TypedRouteTest {
    @Test
    fun typedRouteEncodesAndDecodesPayload() {
        val navController = NavController()

        navController.navigate(DemoRoute("from-chat"))

        val entry = navController.currentDestination
        assertNotNull(entry)
        assertEquals("from-chat", entry.toRoute<DemoRoute>()?.value)
    }

    @Test
    fun queryArgumentsAreParsed() {
        val navController = NavController()
        navController.navigate("help?foo=bar&empty=")

        val entry = navController.currentDestination
        assertNotNull(entry)
        assertEquals("bar", entry.arguments["foo"])
        assertEquals("", entry.arguments["empty"])
    }
}
