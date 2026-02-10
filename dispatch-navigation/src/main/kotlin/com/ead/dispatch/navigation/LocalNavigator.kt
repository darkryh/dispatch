package com.ead.dispatch.navigation

import com.ead.dispatch.runtime.staticCompositionLocalOf

interface Navigator {
    fun <T : NavKey> navigate(key: T)

    fun popBackStack(): Boolean
}

val LocalNavigator =
    staticCompositionLocalOf<Navigator> {
        error("No Navigator provided. Ensure you're inside NavDisplay.")
    }
