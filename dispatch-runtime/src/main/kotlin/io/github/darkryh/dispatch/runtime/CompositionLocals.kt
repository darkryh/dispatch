package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.theme.DispatchTheme

/**
 * CompositionLocal providing the current [Terminal].
 *
 * Static: the terminal reference is provided once at setup and never changes, so per-read
 * state tracking is pure overhead.
 */
val LocalTerminal =
    staticCompositionLocalOf<Terminal> {
        error("No Terminal provided. Ensure you're inside a DispatchApplication.")
    }

/**
 * CompositionLocal providing the current [DispatchTheme].
 */
val LocalTheme = compositionLocalOf { DispatchTheme.Dark }

/**
 * CompositionLocal providing the current [SavedStateHandle].
 */
val LocalSavedStateHandle = compositionLocalOf<SavedStateHandle?> { null }

/**
 * CompositionLocal providing the current [DispatchScope].
 */
val LocalDispatchScope =
    staticCompositionLocalOf<DispatchScope> {
        error("No DispatchScope provided. Ensure you're inside a DispatchApplication.")
    }

/**
 * CompositionLocal providing parsed arguments and flags.
 *
 * Static: provided once with a stable reference.
 */
val LocalDispatchArgs =
    staticCompositionLocalOf<DispatchArgs> {
        error("No DispatchArgs provided. Ensure you're inside a DispatchApplication.")
    }

/**
 * CompositionLocal providing the combined dispatch context.
 *
 * Static: the provided instance is remembered in DispatchApplication so it stays referentially
 * stable across recompositions (see the `remember(scope, args, config)` at the provider site).
 */
val LocalDispatchContext =
    staticCompositionLocalOf<DispatchContext> {
        error("No DispatchContext provided. Ensure you're inside a DispatchApplication.")
    }

/**
 * CompositionLocal providing the dispatch configuration.
 *
 * Static: provided once with a stable reference.
 */
val LocalDispatchConfig =
    staticCompositionLocalOf<DispatchConfig> {
        error("No DispatchConfig provided. Ensure you're inside a DispatchApplication.")
    }

/**
 * CompositionLocal providing whether the current element is focused.
 */
@Deprecated(
    "Never provided by the framework — it is always false. Read focus through " +
        "LocalFocusRegistry.current.isFocused(token) on a Modifier.focusable(token) node instead.",
)
val LocalFocused = compositionLocalOf { false }

/**
 * CompositionLocal providing whether the current element is enabled.
 */
@Deprecated(
    "Never provided by the framework — it is always true. Pass an enabled parameter to your " +
        "composable (as the built-in widgets do) instead.",
)
val LocalEnabled = compositionLocalOf { true }

/**
 * CompositionLocal providing the current content alpha (opacity).
 */
@Deprecated(
    "Never provided or read by the framework; terminals have no alpha channel. Style text via " +
        "TextStyle (e.g. dim) instead.",
)
val LocalContentAlpha = compositionLocalOf { 1.0f }

/**
 * CompositionLocal providing the absolute position in the terminal.
 */
@Deprecated("Never provided by the framework — it is always Position(0, 0).")
val LocalPosition = compositionLocalOf { Position(0, 0) }

/**
 * Position in the terminal.
 */
data class Position(
    val x: Int,
    val y: Int,
)

/**
 * CompositionLocal providing the keyboard event interceptor.
 *
 * This allows widgets like CommandPalette to intercept keyboard events
 * before TextField handles them.
 */
val LocalKeyboardInterceptor =
    staticCompositionLocalOf<KeyboardInterceptor> {
        error("No KeyboardInterceptor provided. Ensure you're inside a DispatchApplication.")
    }

/**
 * CompositionLocal providing the current exit prompt state.
 */
val LocalExitPromptState = compositionLocalOf { ExitPromptState() }

/**
 * CompositionLocal providing focus management for input fields.
 */
val LocalFocusRegistry =
    staticCompositionLocalOf<FocusRegistry> {
        error("No FocusRegistry provided. Ensure you're inside a DispatchApplication.")
    }

/**
 * Optional callback used by navigation hosts to mark a complete screen-content transition.
 * The terminal engine uses this signal to replace the viewport and clear obsolete scrollback.
 */
val LocalScreenTransitionObserver = staticCompositionLocalOf<(() -> Unit)?> { null }
