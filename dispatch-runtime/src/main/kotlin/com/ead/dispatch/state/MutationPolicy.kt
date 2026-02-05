package com.ead.dispatch.state

/**
 * Defines when a state update should be considered a real change.
 */
interface MutationPolicy<T> {
    /**
     * Returns true if [a] and [b] are considered equivalent.
     */
    fun equivalent(a: T, b: T): Boolean
}

/**
 * Treats values as equivalent when they are structurally equal (`==`).
 */
fun <T> structuralEqualityPolicy(): MutationPolicy<T> = object : MutationPolicy<T> {
    override fun equivalent(a: T, b: T): Boolean = a == b
}

/**
 * Treats values as equivalent only when they are the same instance (`===`).
 */
fun <T> referentialEqualityPolicy(): MutationPolicy<T> = object : MutationPolicy<T> {
    override fun equivalent(a: T, b: T): Boolean = a === b
}

/**
 * Treats all values as non-equivalent, always triggering updates.
 */
fun <T> neverEqualPolicy(): MutationPolicy<T> = object : MutationPolicy<T> {
    override fun equivalent(a: T, b: T): Boolean = false
}
