package com.ead.dispatch.state

import kotlin.reflect.KProperty

/**
 * A value holder that triggers recomposition when read during composition.
 *
 * @param T The type of value held by this state.
 */
interface State<out T> {
    /**
     * The current value of this state.
     */
    val value: T
}

/**
 * A mutable value holder that triggers recomposition when changed.
 *
 * Changes to [value] will cause any composable functions that read this state
 * to be scheduled for recomposition.
 *
 * @param T The type of value held by this state.
 */
interface MutableState<T> : State<T> {
    /**
     * The current value of this state. Setting this will trigger recomposition
     * of any composable functions reading this state.
     */
    override var value: T

    /**
     * Destructuring support for `val (value, setValue) = mutableStateOf(...)` pattern.
     */
    operator fun component1(): T

    /**
     * Destructuring support for `val (value, setValue) = mutableStateOf(...)` pattern.
     */
    operator fun component2(): (T) -> Unit
}

/**
 * Property delegate for reading [State.value].
 *
 * Example:
 * ```kotlin
 * val count: State<Int> = ...
 * val currentCount by count  // Reads count.value
 * ```
 */
operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value

/**
 * Property delegate for reading and writing [MutableState.value].
 *
 * Example:
 * ```kotlin
 * var count by mutableStateOf(0)
 * count++  // Writes to MutableState.value
 * ```
 */
operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

/**
 * Creates a [MutableState] initialized with the given [value].
 *
 * Changes to the state will trigger recomposition of any composable
 * functions that read this state.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun Counter() {
 *     var count by remember { mutableStateOf(0) }
 *
 *     Button(onClick = { count++ }) {
 *         Text("Count: $count")
 *     }
 * }
 * ```
 *
 * @param value The initial value for this state.
 * @return A [MutableState] initialized with the given value.
 */
fun <T> mutableStateOf(
    value: T,
    policy: MutationPolicy<T> = structuralEqualityPolicy(),
): MutableState<T> = SnapshotMutableState(value, policy)

/**
 * Creates a [State] that derives its value from other state objects.
 *
 * The [calculation] will be re-executed whenever any state it reads changes.
 *
 * Example:
 * ```kotlin
 * val firstName by mutableStateOf("John")
 * val lastName by mutableStateOf("Doe")
 * val fullName by derivedStateOf { "$firstName $lastName" }
 * ```
 *
 * @param calculation The function that computes the derived value.
 * @return A [State] whose value is computed from other states.
 */
fun <T> derivedStateOf(
    calculation: () -> T
): State<T> = DerivedState(calculation, structuralEqualityPolicy())

/**
 * Creates a [State] that derives its value from other state objects with a mutation policy.
 *
 * @param policy Determines when derived values are considered equivalent.
 * @param calculation The function that computes the derived value.
 * @return A [State] whose value is computed from other states.
 */
fun <T> derivedStateOf(
    policy: MutationPolicy<T>,
    calculation: () -> T
): State<T> = DerivedState(calculation, policy)
