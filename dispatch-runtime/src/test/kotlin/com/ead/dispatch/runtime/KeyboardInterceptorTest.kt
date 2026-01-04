package com.ead.dispatch.runtime

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyboardInterceptorTest {
    @Test
    fun `interceptors run last registered first`() {
        val interceptor = KeyboardInterceptor()
        val calls = mutableListOf<String>()

        interceptor.register { calls.add("first"); false }
        interceptor.register { calls.add("second"); false }

        interceptor.tryIntercept(KeyboardEvent("ArrowUp"))

        assertEquals(listOf("second", "first"), calls)
    }

    @Test
    fun `interceptor stops when event is consumed`() {
        val interceptor = KeyboardInterceptor()
        val calls = mutableListOf<String>()

        interceptor.register { calls.add("fallback"); false }
        interceptor.register { calls.add("consume"); true }

        val handled = interceptor.tryIntercept(KeyboardEvent("ArrowDown"))

        assertTrue(handled)
        assertEquals(listOf("consume"), calls)
    }
}
