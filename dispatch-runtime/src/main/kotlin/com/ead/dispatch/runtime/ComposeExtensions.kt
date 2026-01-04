package com.ead.dispatch.runtime

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.state.MutableState
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember

// ═══════════════════════════════════════════════════════════════════════════════
// Additional CompositionLocals for Dispatch
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * CompositionLocal providing the current terminal width.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun MyComponent() {
 *     val width = LocalTerminalWidth.current
 *     Text("─".repeat(width))
 * }
 * ```
 */
val LocalTerminalWidth = compositionLocalOf { 80 }

/**
 * CompositionLocal providing the current terminal height.
 */
val LocalTerminalHeight = compositionLocalOf { 24 }

// ═══════════════════════════════════════════════════════════════════════════════
// Enhanced Remember Functions
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Remember a mutable state value.
 *
 * Shorthand for `remember { mutableStateOf(value) }`.
 *
 * Example:
 * ```kotlin
 * @Dispatchable
 * fun Counter() {
 *     var count by rememberState(0)
 *     Button(onClick = { count++ }) {
 *         Text("Count: $count")
 *     }
 * }
 * ```
 *
 * @param value The initial value.
 * @return A [MutableState] that persists across recompositions.
 */
@Dispatchable
fun <T> rememberState(value: T): MutableState<T> {
    return remember { mutableStateOf(value) }
}

/**
 * Remember a mutable state with a key.
 *
 * The state will be reset if the key changes.
 *
 * @param key The key to track for changes.
 * @param value The initial value.
 * @return A [MutableState] that persists across recompositions.
 */
@Dispatchable
fun <T> rememberState(key: Any?, value: T): MutableState<T> {
    return remember(key) { mutableStateOf(value) }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Utility Composables
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Access the current [DispatchScope] in a composable.
 */
@Dispatchable
fun dispatchScope(): DispatchScope {
    return LocalDispatchScope.current
        ?: error("No DispatchScope provided. Make sure you're inside a DispatchApplication.")
}

/** Access the current terminal width in a composable. */
@Dispatchable
fun terminalWidth(): Int = LocalTerminalWidth.current

/** Access the current terminal height in a composable. */
@Dispatchable
fun terminalHeight(): Int = LocalTerminalHeight.current

// ═══════════════════════════════════════════════════════════════════════════════
// Callback Helpers
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Remember a callback that captures the latest values.
 *
 * Similar to `rememberUpdatedState` in Jetpack Compose.
 *
 * @param callback The callback to remember.
 * @return A stable reference to the callback.
 */
@Dispatchable
fun <T> rememberCallback(callback: T): T {
    val state = remember { mutableStateOf(callback) }
    state.value = callback
    return state.value
}
