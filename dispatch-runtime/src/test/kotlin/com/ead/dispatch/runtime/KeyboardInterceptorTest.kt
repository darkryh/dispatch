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

    @Test
    fun `interceptors honor priority then registration order`() {
        val interceptor = KeyboardInterceptor()
        val calls = mutableListOf<String>()

        interceptor.register(priority = 1) { calls.add("p1-first"); false }
        interceptor.register(priority = 0) { calls.add("p0-first"); false }
        interceptor.register(priority = 1) { calls.add("p1-second"); false }

        interceptor.tryIntercept(KeyboardEvent("ArrowUp"))

        assertEquals(listOf("p1-second", "p1-first", "p0-first"), calls)
    }

    @Test
    fun `child interceptor falls back to parent when not consumed`() {
        val parent = KeyboardInterceptor()
        val child = KeyboardInterceptor(parent)
        val calls = mutableListOf<String>()

        parent.register { calls.add("parent"); true }
        child.register { calls.add("child"); false }

        val handled = child.tryIntercept(KeyboardEvent("ArrowDown"))

        assertTrue(handled)
        assertEquals(listOf("child", "parent"), calls)
    }
}
