package com.ead.dispatch.navigation

import com.ead.dispatch.lifecycle.LifecycleOwner
import androidx.compose.runtime.compositionLocalOf

/**
 * Current back stack entry available to composables.
 */
val LocalNavBackStackEntry = compositionLocalOf<NavBackStackEntry<*>?> { null }

/**
 * Current lifecycle owner for the active destination.
 */
val LocalLifecycleOwner = compositionLocalOf<LifecycleOwner?> { null }
