package io.github.darkryh.dispatch.navigation

import androidx.compose.runtime.compositionLocalOf
import io.github.darkryh.dispatch.lifecycle.LifecycleOwner

/**
 * Current back stack entry available to composables.
 */
val LocalNavBackStackEntry = compositionLocalOf<NavBackStackEntry<*>?> { null }

/**
 * Current lifecycle owner for the active destination.
 */
val LocalLifecycleOwner = compositionLocalOf<LifecycleOwner?> { null }
