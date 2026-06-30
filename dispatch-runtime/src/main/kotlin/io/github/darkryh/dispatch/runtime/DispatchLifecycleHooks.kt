package io.github.darkryh.dispatch.runtime

/**
 * Lifecycle hook registry for runtime shutdown.
 */
interface DispatchLifecycleHooks {
    fun onExit(action: () -> Unit)
}
