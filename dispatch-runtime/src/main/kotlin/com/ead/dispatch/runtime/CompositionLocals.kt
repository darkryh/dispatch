package com.ead.dispatch.runtime

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.ead.dispatch.theme.DispatchTheme
import com.github.ajalt.mordant.terminal.Terminal

/**
 * CompositionLocal providing the current [Terminal].
 */
val LocalTerminal = compositionLocalOf<Terminal> {
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
val LocalDispatchScope = compositionLocalOf<DispatchScope> {
    error("No DispatchScope provided. Ensure you're inside a DispatchApplication.")
}

/**
 * CompositionLocal providing parsed arguments and flags.
 */
val LocalDispatchArgs = compositionLocalOf<DispatchArgs> {
    error("No DispatchArgs provided. Ensure you're inside a DispatchApplication.")
}

/**
 * CompositionLocal providing the combined dispatch context.
 */
val LocalDispatchContext = compositionLocalOf<DispatchContext> {
    error("No DispatchContext provided. Ensure you're inside a DispatchApplication.")
}

/**
 * CompositionLocal providing the dispatch configuration.
 */
val LocalDispatchConfig = compositionLocalOf<DispatchConfig> {
    error("No DispatchConfig provided. Ensure you're inside a DispatchApplication.")
}

/**
 * CompositionLocal providing whether the current element is focused.
 */
val LocalFocused = compositionLocalOf { false }

/**
 * CompositionLocal providing whether the current element is enabled.
 */
val LocalEnabled = compositionLocalOf { true }

/**
 * CompositionLocal providing the current content alpha (opacity).
 */
val LocalContentAlpha = compositionLocalOf { 1.0f }

/**
 * CompositionLocal providing the absolute position in the terminal.
 */
val LocalPosition = compositionLocalOf { Position(0, 0) }

/**
 * Position in the terminal.
 */
data class Position(val x: Int, val y: Int)

/**
 * CompositionLocal providing the keyboard event interceptor.
 *
 * This allows widgets like CommandPalette to intercept keyboard events
 * before InputTextField handles them.
 */
val LocalKeyboardInterceptor = staticCompositionLocalOf { KeyboardInterceptor() }

/**
 * CompositionLocal providing the current exit prompt state.
 */
val LocalExitPromptState = compositionLocalOf { ExitPromptState() }

/**
 * CompositionLocal providing focus management for input fields.
 */
val LocalFocusRegistry = staticCompositionLocalOf { FocusRegistry() }
