package com.ead.dispatch.state

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Create a [Flow] that emits values from [block] whenever any state read inside it changes.
 */
@Suppress("CognitiveComplexMethod")
fun <T> snapshotFlow(block: () -> T): Flow<T> = callbackFlow {
    val changes = Channel<Unit>(Channel.CONFLATED)
    val observer = StateObserver { changes.trySend(Unit) }

    var dependencies: Set<StateObserverTarget> = emptySet()
    var hasValue = false
    var lastValue: T? = null

    fun updateDependencies(newDeps: Set<StateObserverTarget>) {
        val previous = dependencies
        dependencies = newDeps

        for (dep in previous) {
            if (dep !in newDeps) {
                dep.removeObserver(observer)
            }
        }

        for (dep in newDeps) {
            if (dep !in previous) {
                dep.addObserver(observer)
            }
        }
    }

    fun recompute() {
        val newDeps = LinkedHashSet<StateObserverTarget>()
        val value = StateReadObserver.observeReads({ state ->
            if (state is StateObserverTarget) {
                newDeps.add(state)
            }
        }) { block() }

        updateDependencies(newDeps)

        if (!hasValue || value != lastValue) {
            hasValue = true
            lastValue = value
            trySend(value)
        }
    }

    val job = launch {
        for (ignored in changes) {
            recompute()
        }
    }

    recompute()

    awaitClose {
        job.cancel()
        changes.close()
        dependencies.forEach { it.removeObserver(observer) }
        dependencies = emptySet()
    }
}
