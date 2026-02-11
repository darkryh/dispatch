package com.ead.dispatch.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.Volatile

/**
 * Manages recomposition scheduling and execution.
 *
 * The Recomposer tracks which composition scopes need to be recomposed
 * and schedules recomposition on the appropriate dispatcher.
 */
class Recomposer(
    private val coroutineScope: CoroutineScope
) {
    /**
     * Scopes that need recomposition.
     */
    private val invalidScopes = ConcurrentHashMap.newKeySet<Any>()

    /**
     * Channel for recomposition requests.
     */
    private val recomposeChannel = Channel<Unit>(Channel.CONFLATED)

    /**
     * Flow that emits when recomposition is needed.
     */
    val recomposeFlow: Flow<Unit> = recomposeChannel.receiveAsFlow()

    /**
     * Whether the recomposer is running.
     */
    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Callbacks to invoke during recomposition, grouped by scope.
     */
    private val compositionCallbacks = ConcurrentHashMap<Any, CopyOnWriteArrayList<() -> Unit>>()
    private val defaultScope = Any()

    /**
     * Register a composition to be managed by this recomposer.
     */
    fun registerComposition(callback: () -> Unit) {
        registerComposition(defaultScope, callback)
    }

    /**
     * Unregister a composition.
     */
    fun unregisterComposition(callback: () -> Unit) {
        unregisterComposition(defaultScope, callback)
    }

    /**
     * Register a composition with a scope token.
     */
    fun registerComposition(scope: Any, callback: () -> Unit) {
        compositionCallbacks.compute(scope) { _, callbacks ->
            (callbacks ?: CopyOnWriteArrayList()).apply { add(callback) }
        }
    }

    /**
     * Unregister a composition for a specific scope.
     */
    fun unregisterComposition(scope: Any, callback: () -> Unit) {
        val callbacks = compositionCallbacks[scope] ?: return
        callbacks.remove(callback)
        if (callbacks.isEmpty()) {
            compositionCallbacks.remove(scope, callbacks)
        }
    }

    /**
     * Mark a scope as needing recomposition.
     */
    fun invalidate(scope: Any) {
        invalidScopes.add(scope)
        recomposeChannel.trySend(Unit)
    }

    /**
     * Request immediate recomposition.
     */
    fun requestRecomposition() {
        recomposeChannel.trySend(Unit)
    }

    /**
     * Start the recomposition loop.
     */
    fun start(): Job {
        isRunning = true
        return coroutineScope.launch {
            recomposeFlow.collect {
                if (isRunning) {
                    performRecomposition()
                }
            }
        }
    }

    /**
     * Stop the recomposer.
     */
    fun stop() {
        isRunning = false
        recomposeChannel.close()
    }

    /**
     * Perform recomposition for all invalid scopes.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun performRecomposition() {
        if (invalidScopes.isEmpty() && compositionCallbacks.isEmpty()) return

        val scopesToRecompose =
            if (invalidScopes.isEmpty()) {
                compositionCallbacks.keys.toSet()
            } else {
                invalidScopes.toSet()
            }
        invalidScopes.clear()

        for (scope in scopesToRecompose) {
            val callbacks = compositionCallbacks[scope] ?: continue
            callbacks.forEach { callback ->
                try {
                    callback()
                } catch (e: RuntimeException) {
                    // Log error but continue with other compositions
                    System.err.println("Recomposition error: ${e.message}")
                }
            }
        }
    }

    /**
     * Check if a scope needs recomposition.
     */
    fun needsRecomposition(scope: Any): Boolean = scope in invalidScopes

    /**
     * Create a child scope for composition effects that should stay dispatcher-aligned
     * with this recomposer while still being independently cancellable.
     */
    internal fun createEffectScope(): CoroutineScope {
        val parentJob = coroutineScope.coroutineContext[Job]
        val childJob = if (parentJob != null) SupervisorJob(parentJob) else SupervisorJob()
        return CoroutineScope(coroutineScope.coroutineContext + childJob)
    }

    /**
     * Check if there are any pending changes that need recomposition.
     */
    fun hasPendingChanges(): Boolean = invalidScopes.isNotEmpty()

    companion object {
        /**
         * Thread-local for tracking the current composition scope.
         */
        private val currentScopeLocal = ThreadLocal<Any?>()

        /**
         * Thread-local for the current recomposer.
         */
        internal val currentRecomposerLocal = ThreadLocal<Recomposer?>()

        /**
         * Get the current composition scope being tracked.
         */
        val currentScope: Any?
            get() = currentScopeLocal.get()

        /**
         * Get the current recomposer.
         */
        val current: Recomposer?
            get() = currentRecomposerLocal.get()

        /**
         * Set the current composition scope.
         */
        internal fun setCurrentScope(scope: Any?) {
            if (scope != null) {
                currentScopeLocal.set(scope)
            } else {
                currentScopeLocal.remove()
            }
        }

        /**
         * Set the current recomposer.
         */
        internal fun setCurrentRecomposer(recomposer: Recomposer?) {
            if (recomposer != null) {
                currentRecomposerLocal.set(recomposer)
            } else {
                currentRecomposerLocal.remove()
            }
        }

        /**
         * Invalidate a scope on the current recomposer.
         */
        fun invalidateScope(scope: Any) {
            current?.invalidate(scope)
        }

        /**
         * Execute a block with the given scope as current.
         */
        fun <T> withScope(scope: Any, block: () -> T): T {
            val previous = currentScopeLocal.get()
            try {
                currentScopeLocal.set(scope)
                return block()
            } finally {
                if (previous != null) {
                    currentScopeLocal.set(previous)
                } else {
                    currentScopeLocal.remove()
                }
            }
        }

        /**
         * Execute a block with the given recomposer as current.
         */
        fun <T> withRecomposer(recomposer: Recomposer, block: () -> T): T {
            val previous = currentRecomposerLocal.get()
            try {
                currentRecomposerLocal.set(recomposer)
                return block()
            } finally {
                if (previous != null) {
                    currentRecomposerLocal.set(previous)
                } else {
                    currentRecomposerLocal.remove()
                }
            }
        }
    }
}
