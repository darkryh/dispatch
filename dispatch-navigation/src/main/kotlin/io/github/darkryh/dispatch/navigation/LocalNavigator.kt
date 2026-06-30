package io.github.darkryh.dispatch.navigation

import androidx.compose.runtime.staticCompositionLocalOf

val LocalNavigator =
    staticCompositionLocalOf<Navigator> {
        error("No Navigator provided. Ensure you're inside NavDisplay.")
    }
