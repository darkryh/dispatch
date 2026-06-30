package io.github.darkryh.dispatch.lifecycle

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
        // Snapshot to tolerate observers removing themselves during dispatch.
        observers.toList().forEach { it(state) }
        if (state == LifecycleState.DESTROYED) {
            // Release observer references so anything they close over can be
            // garbage collected once the registry reaches its terminal state.
            observers.clear()
        }
    }

    /**
     * Register an observer and receive a handle that removes it again.
     *
     * Consumers should register via a [DisposableEffect]-style scope and call
     * [AutoCloseable.close] on dispose to avoid retaining captured references.
     */
    fun addObserver(observer: (LifecycleState) -> Unit): AutoCloseable {
        observers += observer
        return AutoCloseable { removeObserver(observer) }
    }

    /**
     * Remove a previously registered observer. Safe to call more than once.
     */
    fun removeObserver(observer: (LifecycleState) -> Unit) {
        observers.remove(observer)
    }
}
