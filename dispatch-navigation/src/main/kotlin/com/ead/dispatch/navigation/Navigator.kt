package com.ead.dispatch.navigation

interface Navigator {
    fun <T : NavKey> navigate(key: T)

    fun popBackStack(): Boolean
}
