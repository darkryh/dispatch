package com.ead.dispatch.state

import com.ead.dispatch.runtime.Recomposer

internal interface DerivedStateDependency {
    fun addDependent(dependent: Any)
    fun removeDependent(dependent: Any)
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
    private val calculation: () -> T
) : State<T>, DerivedStateDependency {

    @Volatile
    private var cachedValue: T? = null

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

    override val value: T
        get() {
            Recomposer.currentScope?.let { scope ->
                readers.add(scope)
            }
            DerivedStateObserver.recordDependency(this)

            if (!isValid) {
                recalculate()
            }

            @Suppress("UNCHECKED_CAST")
            return cachedValue as T
        }

    /**
     * Recalculate the derived value.
     */
    private fun recalculate() {
        dependencies.forEach { it.removeDependent(this) }
        dependencies.clear()

        cachedValue = DerivedStateObserver.observe(this) {
            calculation()
        }
        isValid = true
    }

    /**
     * Invalidate this derived state, forcing recalculation on next read.
     */
    fun invalidate() {
        if (!isValid) return
        isValid = false
        dependents.forEach { it.invalidate() }
        readers.forEach { scope ->
            Recomposer.invalidateScope(scope)
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
}
