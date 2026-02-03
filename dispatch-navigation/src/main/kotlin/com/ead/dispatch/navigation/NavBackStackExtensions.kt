package com.ead.dispatch.navigation

/**
 * Navigate by pushing a key onto the back stack.
 */
fun <T : NavKey> NavBackStack<T>.navigate(key: T) {
    add(key)
}

/**
 * Pop the back stack.
 *
 * @return True if an entry was popped.
 */
fun <T : NavKey> NavBackStack<T>.popBackStack(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}

/**
 * Check if we can navigate back.
 */
fun NavBackStack<*>.canGoBack(): Boolean = size > 1
