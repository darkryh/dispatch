package com.ead.dispatch.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
@SerialName("demo")
private data class DemoRoute(
    val value: String,
) : NavKey

class TypedRouteTest {
    @Test
    fun navKeyEncodingRoundTrips() {
        val original = DemoRoute("from-chat")
        val encoded = encodeNavKeyForSave(original)
        val decoded = decodeNavKeyFromSave(encoded) as DemoRoute
        assertEquals("from-chat", decoded.value)
    }
}
