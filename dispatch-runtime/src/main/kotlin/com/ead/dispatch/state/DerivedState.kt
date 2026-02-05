package com.ead.dispatch.state

import com.ead.dispatch.runtime.Recomposer
import java.util.concurrent.CopyOnWriteArrayList

internal interface DerivedStateDependency {
    fun addDependent(dependent: Any)
    fun removeDependent(dependent: Any)
}

internal fun interface StateObserver {
    fun onChanged()
}

internal interface StateObserverTarget {
    fun addObserver(observer: StateObserver)
    fun removeObserver(observer: StateObserver)
}

internal object StateReadObserver {
    private val current = ThreadLocal<(Any) -> Unit>()

    fun <T> observeReads(observer: (Any) -> Unit, block: () -> T): T {
        val previous = current.get()
        current.set(observer)
        return try {
            block()
        } finally {
            if (previous != null) {
                current.set(previous)
            } else {
                current.remove()
            }
        }
    }

    fun recordRead(state: Any) {
        current.get()?.invoke(state)
    }
}

internal object DerivedStateObserver {
    private val currentDerived = ThreadLocal<DerivedState<*>?>()

    fun <T> observe(derivedState: DerivedState<*>, block: () -> T): T {
        val previous = currentDerived.get()
        currentDerived.set(derivedState)
        return try {
            block()
        } finally {
            currentDerived.set(previous)
        }
    }

    fun recordDependency(dependency: DerivedStateDependency) {
        currentDerived.get()?.registerDependency(dependency)
    }
}

/**
 * A [State] whose value is derived from other state objects.
 *
 * The calculation is re-executed whenever any state it reads changes.
 * The result is cached until dependencies change.
 */
internal class DerivedState<T>(
    private val calculation: () -> T,
    private val policy: MutationPolicy<T>,
) : State<T>, DerivedStateDependency, StateObserverTarget {

    @Volatile
    private var cachedValue: T? = null

    @Volatile
    private var hasValue: Boolean = false

    @Volatile
    private var isValid: Boolean = false

    /**
     * Dependencies that were read during the last calculation.
     */
    private val dependencies = mutableSetOf<DerivedStateDependency>()

    /**
     * Downstream derived states that depend on this one.
     */
    private val dependents = mutableSetOf<DerivedState<*>>()

    /**
     * Scopes that read this derived state.
     */
    private val readers = mutableSetOf<Any>()

    private val observers = CopyOnWriteArrayList<StateObserver>()

    override val value: T
        get() {
            Recomposer.currentScope?.let { scope ->
                readers.add(scope)
            }
            DerivedStateObserver.recordDependency(this)
            StateReadObserver.recordRead(this)

            if (!isValid) {
                val previous = cachedValue
                val newValue = computeValue()
                commitValue(previous, newValue, hasValue)
            }

            @Suppress("UNCHECKED_CAST")
            return cachedValue as T
        }

    /**
     * Recalculate the derived value.
     */
    private fun computeValue(): T {
        dependencies.forEach { it.removeDependent(this) }
        dependencies.clear()

        return DerivedStateObserver.observe(this) {
            calculation()
        }
    }

    private fun commitValue(previousValue: T?, newValue: T, hadPrevious: Boolean): Boolean {
        val changed =
            if (!hadPrevious) {
                true
            } else {
                @Suppress("UNCHECKED_CAST")
                !policy.equivalent(previousValue as T, newValue)
            }

        if (changed) {
            cachedValue = newValue
        } else {
            cachedValue = previousValue
        }

        hasValue = true
        isValid = true
        return changed
    }

    /**
     * Invalidate this derived state, forcing recalculation on next read.
     */
    fun invalidate() {
        if (!isValid) return
        if (readers.isEmpty() && dependents.isEmpty() && observers.isEmpty()) {
            isValid = false
            return
        }

        val previousValue = cachedValue
        val newValue = computeValue()
        val changed = commitValue(previousValue, newValue, hasValue)

        if (changed) {
            dependents.forEach { it.invalidate() }
            readers.forEach { scope ->
                Recomposer.invalidateScope(scope)
            }
            observers.forEach { it.onChanged() }
        }
    }

    override fun toString(): String = "DerivedState(value=${if (isValid) cachedValue else "<not computed>"})"

    internal fun registerDependency(dependency: DerivedStateDependency) {
        if (dependencies.add(dependency)) {
            dependency.addDependent(this)
        }
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
}
