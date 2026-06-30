package io.github.darkryh.dispatch.navigation

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
    // Deterministic disposal: tell the host (NavDisplay) which content keys
    // survive so it can clear ViewModel scopes for popped entries immediately,
    // without waiting for a recomposition diff. The diff remains a backstop.
    onEntriesRemoved?.invoke(map { stableContentKey(it) }.toSet())
    return true
}

/**
 * Check if we can navigate back.
 */
fun NavBackStack<*>.canGoBack(): Boolean = size > 1
