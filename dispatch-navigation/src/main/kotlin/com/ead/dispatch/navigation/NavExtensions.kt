package com.ead.dispatch.navigation

import kotlin.collections.iterator

/**
 * Extension functions and utilities for navigation.
 */

/**
 * Navigate with options using a builder.
 */
fun NavController.navigate(route: String, builder: NavOptions.Builder.() -> Unit) {
    navigate(route, navOptions(builder))
}

/**
 * Navigate and pop to a destination.
 */
fun NavController.navigatePopUpTo(route: String, popUpTo: String, inclusive: Boolean = false) {
    navigate(route, NavOptions(popUpTo = popUpTo, inclusive = inclusive))
}

/**
 * Navigate as single top (won't add duplicate if already on top).
 */
fun NavController.navigateSingleTop(route: String) {
    navigate(route, NavOptions(launchSingleTop = true))
}

/**
 * Pop back or finish.
 *
 * @param onFinish Called if there's nothing to pop back to.
 */
fun NavController.popBackStackOrFinish(onFinish: () -> Unit) {
    if (!popBackStack()) {
        onFinish()
    }
}

/**
 * Get argument as Int.
 */
fun NavBackStackEntry.getIntArgument(key: String): Int? =
    arguments[key]?.toIntOrNull()

/**
 * Get argument as Int with default.
 */
fun NavBackStackEntry.getIntArgument(key: String, default: Int): Int =
    arguments[key]?.toIntOrNull() ?: default

/**
 * Get required Int argument.
 */
fun NavBackStackEntry.requireIntArgument(key: String): Int =
    arguments[key]?.toIntOrNull()
        ?: throw IllegalArgumentException("Missing or invalid int argument: $key")

/**
 * Get argument as Long.
 */
fun NavBackStackEntry.getLongArgument(key: String): Long? =
    arguments[key]?.toLongOrNull()

/**
 * Get argument as Boolean.
 */
fun NavBackStackEntry.getBooleanArgument(key: String): Boolean? =
    arguments[key]?.toBooleanStrictOrNull()

/**
 * Route builder for type-safe navigation.
 *
 * Example:
 * ```kotlin
 * object Routes {
 *     const val HOME = "home"
 *     fun detail(id: String) = "detail/$id"
 *     fun settings(tab: String = "general") = "settings?tab=$tab"
 * }
 *
 * // Navigate
 * navController.navigate(Routes.detail("123"))
 * ```
 */
object RouteBuilder {
    /**
     * Build a route with path parameters.
     */
    fun build(base: String, vararg params: Pair<String, Any>): String {
        var route = base
        params.forEach { (key, value) ->
            route = route.replace("{$key}", value.toString())
        }
        return route
    }

    /**
     * Build a route with query parameters.
     */
    fun buildWithQuery(base: String, vararg params: Pair<String, Any?>): String {
        val queryParams = params
            .filter { it.second != null }
            .joinToString("&") { "${it.first}=${it.second}" }

        return if (queryParams.isNotEmpty()) "$base?$queryParams" else base
    }
}

/**
 * Deep link configuration.
 */
data class DeepLink(
    /**
     * URI pattern (e.g., "myapp://detail/{id}").
     */
    val uriPattern: String,

    /**
     * Action to perform (optional).
     */
    val action: String? = null,

    /**
     * MIME type (optional).
     */
    val mimeType: String? = null,
)

/**
 * Parse a deep link URI into a route.
 */
fun parseDeepLink(uri: String, patterns: Map<String, String>): String? {
    for ((pattern, route) in patterns) {
        val match = matchDeepLink(uri, pattern)
        if (match != null) {
            return buildRouteFromMatch(route, match)
        }
    }
    return null
}

private fun matchDeepLink(uri: String, pattern: String): Map<String, String>? {
    // Simple pattern matching: myapp://detail/{id}
    val uriParts = uri.removePrefix("myapp://").split("/")
    val patternParts = pattern.removePrefix("myapp://").split("/")

    if (uriParts.size != patternParts.size) return null

    val matches = mutableMapOf<String, String>()

    for ((uriPart, patternPart) in uriParts.zip(patternParts)) {
        if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
            val key = patternPart.removeSurrounding("{", "}")
            matches[key] = uriPart
        } else if (uriPart != patternPart) {
            return null
        }
    }

    return matches
}

private fun buildRouteFromMatch(route: String, matches: Map<String, String>): String {
    var result = route
    for ((key, value) in matches) {
        result = result.replace("{$key}", value)
    }
    return result
}

/**
 * Handle back press.
 *
 * Returns true if the back press was handled.
 */
fun NavController.handleBackPress(): Boolean = popBackStack()

/**
 * Back press handler interface.
 */
fun interface BackPressHandler {
    /**
     * Handle a back press.
     * @return True if handled, false to allow default behavior.
     */
    fun onBackPressed(): Boolean
}

/**
 * Navigation result for passing data back.
 */
class NavigationResult<T> {
    private var result: T? = null
    private var hasResult = false
    private val listeners = mutableListOf<(T) -> Unit>()

    /**
     * Set the result.
     */
    fun setResult(value: T) {
        result = value
        hasResult = true
        listeners.forEach { it(value) }
    }

    /**
     * Get the result (if set).
     */
    fun getResult(): T? = if (hasResult) result else null

    /**
     * Check if result is set.
     */
    fun hasResult(): Boolean = hasResult

    /**
     * Add a listener for when result is set.
     */
    fun addResultListener(listener: (T) -> Unit) {
        listeners.add(listener)
        // If result already set, notify immediately
        if (hasResult && result != null) {
            @Suppress("UNCHECKED_CAST")
            listener(result as T)
        }
    }

    /**
     * Clear the result.
     */
    fun clear() {
        result = null
        hasResult = false
    }
}
