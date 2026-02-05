package com.ead.dispatch.state

import com.ead.dispatch.runtime.Recomposer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.Volatile

/**
 * Default implementation of [MutableState] that integrates with the snapshot system.
 *
 * When the value changes, all composable functions that read this state
 * during their last composition will be scheduled for recomposition.
 */
internal class SnapshotMutableState<T>(
    initialValue: T,
    private val policy: MutationPolicy<T>,
) : MutableState<T>, DerivedStateDependency, StateObserverTarget {

    @Volatile
    private var _value: T = initialValue

    /**
     * Tracks which scopes have read this state.
     * When value changes, these scopes need recomposition.
     */
    private val readers = mutableSetOf<Any>()
    private val dependents = mutableSetOf<DerivedState<*>>()
    private val observers = CopyOnWriteArrayList<StateObserver>()

    override var value: T
        get() {
            // Record that current composition scope is reading this state
            Recomposer.currentScope?.let { scope ->
                readers.add(scope)
            }
            DerivedStateObserver.recordDependency(this)
            StateReadObserver.recordRead(this)
            return _value
        }
        set(newValue) {
            val current = _value
            if (policy.equivalent(current, newValue)) return
            _value = newValue
            notifyChanged()
        }

    override fun component1(): T = value

    override fun component2(): (T) -> Unit = { value = it }

    /**
     * Notify the recomposer that this state has changed.
     */
    private fun notifyChanged() {
        dependents.forEach { it.invalidate() }
        // Invalidate all scopes that read this state
        readers.forEach { scope ->
            Recomposer.invalidateScope(scope)
        }
        observers.forEach { it.onChanged() }
    }

    /**
     * Clear reader tracking (called during recomposition).
     */
    internal fun clearReaders() {
        readers.clear()
    }

    override fun addDependent(dependent: Any) {
        val derived = dependent as? DerivedState<*> ?: return
        dependents.add(derived)
    }

    override fun removeDependent(dependent: Any) {
        val derived = dependent as? DerivedState<*> ?: return
        dependents.remove(derived)
    }

    override fun addObserver(observer: StateObserver) {
        observers.add(observer)
    }

    override fun removeObserver(observer: StateObserver) {
        observers.remove(observer)
    }

    override fun toString(): String = "MutableState(value=$_value)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnapshotMutableState<*>) return false
        return _value == other._value
    }

    override fun hashCode(): Int = _value?.hashCode() ?: 0
}
