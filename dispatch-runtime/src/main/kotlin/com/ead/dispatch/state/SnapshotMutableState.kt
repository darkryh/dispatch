package com.ead.dispatch.state

import com.ead.dispatch.runtime.Recomposer
import kotlin.concurrent.Volatile

/**
 * Default implementation of [MutableState] that integrates with the snapshot system.
 *
 * When the value changes, all composable functions that read this state
 * during their last composition will be scheduled for recomposition.
 */
internal class SnapshotMutableState<T>(initialValue: T) : MutableState<T>, DerivedStateDependency {

    @Volatile
    private var _value: T = initialValue

    /**
     * Tracks which scopes have read this state.
     * When value changes, these scopes need recomposition.
     */
    private val readers = mutableSetOf<Any>()
    private val dependents = mutableSetOf<DerivedState<*>>()

    override var value: T
        get() {
            // Record that current composition scope is reading this state
            Recomposer.currentScope?.let { scope ->
                readers.add(scope)
            }
            DerivedStateObserver.recordDependency(this)
            return _value
        }
        set(newValue) {
            if (_value != newValue) {
                _value = newValue
                notifyChanged()
            }
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

    override fun toString(): String = "MutableState(value=$_value)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnapshotMutableState<*>) return false
        return _value == other._value
    }

    override fun hashCode(): Int = _value?.hashCode() ?: 0
}
