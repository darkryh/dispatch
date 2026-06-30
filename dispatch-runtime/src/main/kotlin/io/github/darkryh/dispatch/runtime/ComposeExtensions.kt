package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

// ═══════════════════════════════════════════════════════════════════════════════
// Additional CompositionLocals for Dispatch
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * CompositionLocal providing the current terminal width.
 *
 * Example:
 * ```kotlin
 * @Composable
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
 * @Composable
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
@Composable
fun <T> rememberState(value: T): MutableState<T> = remember { mutableStateOf(value) }

/**
 * Remember a mutable state with a key.
 *
 * The state will be reset if the key changes.
 *
 * @param key The key to track for changes.
 * @param value The initial value.
 * @return A [MutableState] that persists across recompositions.
 */
@Composable
fun <T> rememberState(
    key: Any?,
    value: T,
): MutableState<T> = remember(key) { mutableStateOf(value) }

// ═══════════════════════════════════════════════════════════════════════════════
// Utility Composables
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Access the current [DispatchScope] in a composable.
 */
@Composable
fun dispatchScope(): DispatchScope = LocalDispatchScope.current

@Composable
fun dispatchArgs(): DispatchArgs = LocalDispatchArgs.current

@Composable
fun dispatchContext(): DispatchContext = LocalDispatchContext.current

@Composable
fun dispatchConfig(): DispatchConfig = LocalDispatchConfig.current

/** Access the current terminal width in a composable. */
@Composable
fun terminalWidth(): Int = LocalTerminalWidth.current

/** Access the current terminal height in a composable. */
@Composable
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
@Composable
fun <T> rememberCallback(callback: T): T {
    val state = remember { mutableStateOf(callback) }
    state.value = callback
    return state.value
}
