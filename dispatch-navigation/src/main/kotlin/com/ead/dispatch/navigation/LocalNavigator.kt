package com.ead.dispatch.navigation

import com.ead.dispatch.runtime.staticCompositionLocalOf

val LocalNavigator =
    staticCompositionLocalOf<Navigator> {
        error("No Navigator provided. Ensure you're inside NavDisplay.")
    }
