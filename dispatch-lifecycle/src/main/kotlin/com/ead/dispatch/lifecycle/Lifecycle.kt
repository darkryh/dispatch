package com.ead.dispatch.lifecycle

/**
 * Minimal lifecycle contract so scopes (navigation entries, viewmodels, etc.)
 * can coordinate start/stop and cleanup in the CLI environment.
 */
enum class LifecycleState { INITIALIZED, STARTED, STOPPED, DESTROYED }

interface LifecycleOwner {
    val lifecycle: LifecycleRegistry
}

class LifecycleRegistry(
    initialState: LifecycleState = LifecycleState.INITIALIZED,
) {
    var currentState: LifecycleState = initialState
        private set

    private val observers = mutableListOf<(LifecycleState) -> Unit>()

    fun moveTo(state: LifecycleState) {
        currentState = state
        observers.forEach { it(state) }
    }

    fun addObserver(observer: (LifecycleState) -> Unit) {
        observers += observer
    }
}
