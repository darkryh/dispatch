package com.ead.dispatch.annotation

/**
 * Marks a function as a Dispatchable component.
 *
 * Dispatchable functions can:
 * - Use remember() to retain state across recompositions
 * - Use mutableStateOf() for reactive state
 * - Call other @Dispatchable functions
 * - Access CompositionLocals
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun Counter() {
 *     var count by remember { mutableStateOf(0) }
 *     Text("Count: $count")
 * }
 * ```
 *
 * Note: Unlike Jetpack Compose, this annotation is primarily for documentation
 * and tooling purposes. The runtime does not require compiler plugins.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Dispatchable
