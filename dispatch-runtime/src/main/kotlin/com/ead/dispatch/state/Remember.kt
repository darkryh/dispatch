package com.ead.dispatch.state

import com.ead.dispatch.runtime.currentComposer

private data class RememberCallsiteKey(
    val callsiteId: String,
    val userKey: Any? = NoUserRememberKey,
)

private object NoUserRememberKey

private fun rememberCallsiteId(calculation: () -> Any?): String = calculation::class.java.name

/**
 * Remember a state that always updates to the latest [value] without triggering recomposition.
 *
 * This is useful for capturing the latest value inside side-effect lambdas.
 */
fun <T> rememberUpdatedState(value: T): State<T> {
    val state = remember { UpdatedState(value) }
    state.value = value
    return state
}

private class UpdatedState<T>(override var value: T) : State<T>

/**
 * Remember a value across recompositions.
 *
 * The [calculation] will only be executed during the first composition.
 * On subsequent recompositions, the previously stored value is returned.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun Example() {
 *     val list = remember { mutableListOf<String>() }
 *     // list is preserved across recompositions
 * }
 * ```
 *
 * @param calculation The function to compute the initial value.
 * @return The remembered value.
 */
fun <T> remember(calculation: () -> T): T =
    currentComposer.remember(
        RememberCallsiteKey(callsiteId = rememberCallsiteId(calculation)),
        calculation,
    )

/**
 * Remember a value that depends on [key1].
 *
 * When [key1] changes, [calculation] is re-executed and the new value is stored.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun UserProfile(userId: String) {
 *     val userData = remember(userId) { loadUserData(userId) }
 * }
 * ```
 *
 * @param key1 The key to track for changes.
 * @param calculation The function to compute the value.
 * @return The remembered value.
 */
fun <T> remember(key1: Any?, calculation: () -> T): T =
    currentComposer.remember(
        RememberCallsiteKey(
            callsiteId = rememberCallsiteId(calculation),
            userKey = key1,
        ),
        calculation,
    )

/**
 * Remember a value that depends on [key1] and [key2].
 *
 * When either key changes, [calculation] is re-executed.
 *
 * @param key1 The first key to track.
 * @param key2 The second key to track.
 * @param calculation The function to compute the value.
 * @return The remembered value.
 */
fun <T> remember(key1: Any?, key2: Any?, calculation: () -> T): T =
    remember(key1 to key2, calculation)

/**
 * Remember a value that depends on [key1], [key2], and [key3].
 *
 * @param key1 The first key to track.
 * @param key2 The second key to track.
 * @param key3 The third key to track.
 * @param calculation The function to compute the value.
 * @return The remembered value.
 */
fun <T> remember(
    key1: Any?,
    key2: Any?,
    key3: Any?,
    calculation: () -> T,
): T = remember(Triple(key1, key2, key3), calculation)

/**
 * Remember a value that depends on multiple keys.
 *
 * When any key changes, [calculation] is re-executed.
 *
 * @param keys The keys to track for changes.
 * @param calculation The function to compute the value.
 * @return The remembered value.
 */
fun <T> rememberWithKeys(vararg keys: Any?, calculation: () -> T): T =
    remember(keys.toList(), calculation)

/**
 * Remember a value that will be saved and restored during navigation.
 *
 * Unlike [remember], values stored with [rememberSaveable] survive
 * configuration changes and process death (when supported by the platform).
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun Settings() {
 *     var selectedTab by rememberSaveable { mutableStateOf("general") }
 *     // selectedTab is restored when navigating back to this screen
 * }
 * ```
 *
 * @param calculation The function to compute the initial value.
 * @return The remembered and saveable value.
 */
fun <T> rememberSaveable(calculation: () -> T): T = currentComposer.rememberSaveable(calculation)

/**
 * Remember a value with a custom saver for serialization.
 *
 * @param saver The saver to use for serialization.
 * @param calculation The function to compute the initial value.
 * @return The remembered and saveable value.
 */
fun <T> rememberSaveable(
    saver: Saver<T, Any>,
    calculation: () -> T,
): T = currentComposer.rememberSaveableWithSaver(saver, calculation)

/**
 * Interface for saving and restoring state.
 */
interface Saver<Original, Saveable : Any> {
    /**
     * Convert the original value to a saveable format.
     */
    fun save(value: Original): Saveable?

    /**
     * Restore the original value from the saved format.
     */
    fun restore(value: Saveable): Original?
}

/**
 * Create a simple saver that uses the value directly.
 */
fun <T : Any> autoSaver(): Saver<T, T> = object : Saver<T, T> {
    override fun save(value: T): T = value
    override fun restore(value: T): T = value
}
