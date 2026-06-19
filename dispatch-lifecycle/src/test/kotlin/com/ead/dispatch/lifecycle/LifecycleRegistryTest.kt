package com.ead.dispatch.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LifecycleRegistryTest {
    @Test
    fun `default state is initialized`() {
        val registry = LifecycleRegistry()

        assertEquals(LifecycleState.INITIALIZED, registry.currentState)
    }

    @Test
    fun `initial state can be customized`() {
        val registry = LifecycleRegistry(initialState = LifecycleState.STOPPED)

        assertEquals(LifecycleState.STOPPED, registry.currentState)
    }

    @Test
    fun `moveTo updates state and notifies observers in order`() {
        val registry = LifecycleRegistry()
        val events = mutableListOf<String>()

        registry.addObserver { state -> events.add("first:$state") }
        registry.addObserver { state -> events.add("second:$state") }

        registry.moveTo(LifecycleState.STARTED)

        assertEquals(LifecycleState.STARTED, registry.currentState)
        assertEquals(listOf("first:STARTED", "second:STARTED"), events)
    }

    @Test
    fun `observer sees updated state for each transition`() {
        val registry = LifecycleRegistry()
        val observed = mutableListOf<Pair<LifecycleState, LifecycleState>>()

        registry.addObserver { state ->
            observed.add(state to registry.currentState)
        }

        registry.moveTo(LifecycleState.STARTED)
        registry.moveTo(LifecycleState.STOPPED)

        assertEquals(
            listOf(
                LifecycleState.STARTED to LifecycleState.STARTED,
                LifecycleState.STOPPED to LifecycleState.STOPPED,
            ),
            observed,
        )
    }

    @Test
    fun `adding an observer does not trigger immediately`() {
        val registry = LifecycleRegistry()
        val events = mutableListOf<LifecycleState>()

        registry.addObserver { state -> events.add(state) }

        assertTrue(events.isEmpty())
    }

    @Test
    fun `removeObserver stops notifications`() {
        val registry = LifecycleRegistry()
        val events = mutableListOf<LifecycleState>()
        val observer: (LifecycleState) -> Unit = { events.add(it) }

        registry.addObserver(observer)
        registry.moveTo(LifecycleState.STARTED)
        registry.removeObserver(observer)
        registry.moveTo(LifecycleState.STOPPED)

        assertEquals(listOf(LifecycleState.STARTED), events)
    }

    @Test
    fun `addObserver handle removes observer on close`() {
        val registry = LifecycleRegistry()
        val events = mutableListOf<LifecycleState>()

        val handle = registry.addObserver { events.add(it) }
        registry.moveTo(LifecycleState.STARTED)
        handle.close()
        registry.moveTo(LifecycleState.STOPPED)

        assertEquals(listOf(LifecycleState.STARTED), events)
    }

    @Test
    fun `observers are cleared on DESTROYED and stale observer is not invoked`() {
        val registry = LifecycleRegistry()
        var invocations = 0

        registry.addObserver { invocations++ }
        registry.moveTo(LifecycleState.DESTROYED)
        val afterDestroy = invocations

        // A later transition must not invoke the (now released) observer.
        registry.moveTo(LifecycleState.STARTED)

        assertEquals(afterDestroy, invocations)
    }
}
