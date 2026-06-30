package io.github.darkryh.dispatch.navigation

interface Navigator {
    fun <T : NavKey> navigate(key: T)

    fun popBackStack(): Boolean
}
