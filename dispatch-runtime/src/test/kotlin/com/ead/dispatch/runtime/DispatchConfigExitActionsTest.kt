package com.ead.dispatch.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class DispatchConfigExitActionsTest {
    @Test
    fun `runExitActions executes callbacks once in reverse registration order`() {
        val config = DispatchConfig()
        val calls = mutableListOf<String>()

        config.onExit { calls += "first" }
        config.onExit { calls += "second" }

        config.runExitActions()
        config.runExitActions()

        assertEquals(listOf("second", "first"), calls)
    }

    @Test
    fun `runExitActions continues when one callback fails`() {
        val config = DispatchConfig()
        val calls = mutableListOf<String>()

        config.onExit { calls += "ok-1" }
        config.onExit { error("boom") }
        config.onExit { calls += "ok-2" }

        config.runExitActions()

        assertEquals(listOf("ok-2", "ok-1"), calls)
    }
}
