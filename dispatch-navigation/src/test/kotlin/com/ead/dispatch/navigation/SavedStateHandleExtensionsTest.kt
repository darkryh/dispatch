package com.ead.dispatch.navigation

import com.ead.dispatch.runtime.SavedStateHandle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Serializable
@SerialName("demo-handle")
private data class HandleRoute(val value: String)

class SavedStateHandleExtensionsTest {
    @Test
    fun `decode route reads payload from saved state handle`() {
        val payload = DefaultRouteJson.encodeToString(serializer<HandleRoute>(), HandleRoute("alpha"))
        val handle = SavedStateHandle().apply { this[ROUTE_PAYLOAD_KEY] = payload }

        val decoded = handle.toRoute<HandleRoute>()

        assertEquals("alpha", decoded.value)
    }

    @Test
    fun `decode route throws when payload is missing`() {
        val handle = SavedStateHandle()

        assertFailsWith<IllegalArgumentException> {
            handle.toRoute<HandleRoute>()
        }
    }

}
