package com.ead.dispatch.navigation

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.runtime.SavedStateRegistry
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.setValue
import com.ead.dispatch.lifecycle.LifecycleOwner
import com.ead.dispatch.lifecycle.LifecycleRegistry
import com.ead.dispatch.lifecycle.LifecycleState
import com.ead.dispatch.viewmodel.DefaultViewModelFactory
import com.ead.dispatch.viewmodel.ViewModelFactory
import com.ead.dispatch.viewmodel.ViewModelProvider
import java.net.URLDecoder

/**
 * Controller for navigation between screens.
 *
 * Manages the back stack, state preservation, and navigation transitions.
 *
 * Example:
 * ```kotlin
 * val navController = rememberNavController()
 *
 * NavHost(navController, startDestination = "home") {
 *     screen("home") { HomeScreen(navController) }
 *     screen("detail/{id}") { DetailScreen(it.arguments["id"]!!) }
 * }
 *
 * // Navigate
 * navController.navigate("detail/123")
 * navController.popBackStack()
 * ```
 */
class NavController(
    private val savedStateRegistry: SavedStateRegistry? = null,
    private val viewModelFactory: ViewModelFactory = DefaultViewModelFactory(),
) {
    /**
     * The back stack of navigation entries.
     */
    private val _backStack = mutableListOf<NavBackStackEntry>()

    /**
     * Current back stack (read-only).
     */
    val backStack: List<NavBackStackEntry> get() = _backStack.toList()

    /**
     * Current destination (observable state).
     */
    var currentDestination: NavBackStackEntry? by mutableStateOf(null)
        private set

    /**
     * Navigation event listeners.
     */
    private val listeners = mutableListOf<NavigationListener>()

    /**
     * Navigate to a destination.
     *
     * @param route The destination route (e.g., "home", "detail/123").
     * @param navOptions Navigation options.
     */
    fun navigate(route: String, navOptions: NavOptions = NavOptions.Default) {
        val entry = createEntry(route)
        attachLifecycleCleanup(entry)

        // Apply nav options
        if (navOptions.popUpTo != null) {
            popBackStackInternal(navOptions.popUpTo, navOptions.inclusive)
        }

        if (navOptions.launchSingleTop && currentDestination?.route == route) {
            // Don't add duplicate
            return
        }

        currentDestination?.lifecycleRegistry?.moveTo(LifecycleState.STOPPED)
        entry.lifecycleRegistry.moveTo(LifecycleState.STARTED)

        _backStack.add(entry)
        currentDestination = entry

        notifyListeners(NavigationEvent.Navigate(entry))
    }

    /**
     * Navigate and clear the back stack.
     */
    fun navigateAndClear(route: String) {
        _backStack.forEach { entry ->
            entry.lifecycleRegistry.moveTo(LifecycleState.DESTROYED)
        }
        _backStack.clear()
        currentDestination = null
        navigate(route)
    }

    /**
     * Pop the back stack.
     *
     * @return True if there was an entry to pop.
     */
    fun popBackStack(): Boolean {
        if (_backStack.size <= 1) return false

        val popped = _backStack.removeAt(_backStack.lastIndex)
        popped.lifecycleRegistry.moveTo(LifecycleState.DESTROYED)

        currentDestination = _backStack.lastOrNull()
        currentDestination?.lifecycleRegistry?.moveTo(LifecycleState.STARTED)

        notifyListeners(NavigationEvent.Pop(popped))
        return true
    }

    /**
     * Pop back to a specific destination.
     *
     * @param route The route to pop to.
     * @param inclusive Whether to also pop the target destination.
     * @return True if the destination was found and popped to.
     */
    fun popBackStack(route: String, inclusive: Boolean = false): Boolean {
        return popBackStackInternal(route, inclusive)
    }

    private fun popBackStackInternal(route: String, inclusive: Boolean): Boolean {
        val index = _backStack.indexOfLast { matchesRoute(it.route, route) }
        if (index < 0) return false

        val targetIndex = if (inclusive) index else index + 1
        val poppedEntries = mutableListOf<NavBackStackEntry>()
        while (_backStack.size > targetIndex) {
            val popped = _backStack.removeAt(_backStack.lastIndex)
            popped.lifecycleRegistry.moveTo(LifecycleState.DESTROYED)
            poppedEntries.add(popped)
        }

        currentDestination = _backStack.lastOrNull()
        currentDestination?.lifecycleRegistry?.moveTo(LifecycleState.STARTED)
        poppedEntries.forEach { popped ->
            notifyListeners(NavigationEvent.Pop(popped))
        }
        return true
    }

    /**
     * Check if we can navigate back.
     */
    fun canGoBack(): Boolean = _backStack.size > 1

    /**
     * Get the previous back stack entry (if any).
     */
    fun previousBackStackEntry(): NavBackStackEntry? {
        return if (_backStack.size > 1) _backStack[_backStack.size - 2] else null
    }

    /**
     * Add a navigation listener.
     */
    fun addOnNavigationListener(listener: NavigationListener) {
        listeners.add(listener)
    }

    /**
     * Remove a navigation listener.
     */
    fun removeOnNavigationListener(listener: NavigationListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(event: NavigationEvent) {
        listeners.forEach { it.onNavigationEvent(event) }
    }

    private fun parseArguments(route: String): Map<String, String> {
        val args = mutableMapOf<String, String>()
        val path = normalizeRoute(route)
        val segments = path.split("/").filter { it.isNotEmpty() }
        segments.forEachIndexed { index, value ->
            args["arg$index"] = value
        }

        val query = route.substringAfter("?", "")
        if (query.isNotEmpty()) {
            query.split("&").forEach { pair ->
                if (pair.isEmpty()) return@forEach
                val parts = pair.split("=", limit = 2)
                val key = URLDecoder.decode(parts[0], Charsets.UTF_8)
                if (key.isEmpty()) return@forEach
                val value = if (parts.size > 1) {
                    URLDecoder.decode(parts[1], Charsets.UTF_8)
                } else {
                    ""
                }
                args[key] = value
            }
        }
        return args
    }

    private fun matchesRoute(actual: String, pattern: String): Boolean {
        // Simple matching - exact match or pattern with wildcards
        val actualPath = normalizeRoute(actual)
        val patternPath = normalizeRoute(pattern)
        if (actualPath == patternPath) return true

        val actualParts = actualPath.split("/")
        val patternParts = patternPath.split("/")

        if (actualParts.size != patternParts.size) return false

        return actualParts.zip(patternParts).all { (a, p) ->
            a == p || p.startsWith("{") && p.endsWith("}")
        }
    }

    private fun normalizeRoute(route: String): String = route.substringBefore("?")

    /**
     * Save navigation state.
     */
    fun saveState(): NavigationState {
        return NavigationState(
            routes = _backStack.map { it.route },
            currentIndex = _backStack.size - 1,
        )
    }

    /**
     * Restore navigation state.
     */
    fun restoreState(state: NavigationState) {
        _backStack.clear()
        state.routes.forEach { route ->
            val entry = createEntry(route)
            attachLifecycleCleanup(entry)
            _backStack.add(entry)
        }
        currentDestination = _backStack.lastOrNull()
        currentDestination?.lifecycleRegistry?.moveTo(LifecycleState.STARTED)
    }

    private fun createEntry(route: String): NavBackStackEntry {
        val arguments = parseArguments(route)
        val savedStateRegistry = SavedStateRegistry()
        arguments.forEach { (key, value) -> savedStateRegistry.set(key, value) }
        val savedStateHandle = SavedStateHandle(savedStateRegistry)
        return NavBackStackEntry(
            route = route,
            arguments = arguments,
            savedStateRegistry = savedStateRegistry,
            savedStateHandle = savedStateHandle,
            viewModelProvider = ViewModelProvider(viewModelFactory, savedStateHandle),
        )
    }

    private fun attachLifecycleCleanup(entry: NavBackStackEntry) {
        entry.lifecycleRegistry.addObserver { state ->
            if (state == LifecycleState.DESTROYED) {
                entry.viewModelProvider.clear()
            }
        }
    }

    internal fun updateEntryArguments(
        entry: NavBackStackEntry,
        arguments: Map<String, String>,
    ): NavBackStackEntry {
        if (entry.arguments == arguments) return entry

        arguments.forEach { (key, value) ->
            entry.savedStateHandle[key] = value
        }

        val updated = entry.copy(arguments = arguments)
        val index = _backStack.indexOf(entry)
        if (index >= 0) {
            _backStack[index] = updated
        }
        if (currentDestination == entry) {
            currentDestination = updated
        }
        return updated
    }
}

/**
 * An entry in the navigation back stack.
 */
data class NavBackStackEntry(
    /**
     * The route for this entry.
     */
    val route: String,

    /**
     * Arguments extracted from the route.
     */
    val arguments: Map<String, String> = emptyMap(),

    /**
     * Saved state registry for this entry.
     */
    val savedStateRegistry: SavedStateRegistry = SavedStateRegistry(),
    /**
     * Saved state handle for ViewModel access.
     */
    val savedStateHandle: SavedStateHandle = SavedStateHandle(savedStateRegistry),
    val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(),
    val viewModelProvider: ViewModelProvider = ViewModelProvider(),
) : LifecycleOwner {
    /**
     * Get a required argument.
     */
    fun requireArgument(key: String): String =
        arguments[key] ?: throw IllegalArgumentException("Missing required argument: $key")

    /**
     * Get an optional argument.
     */
    fun getArgument(key: String): String? = arguments[key]

    /**
     * Get an argument with a default value.
     */
    fun getArgument(key: String, default: String): String = arguments[key] ?: default

    override val lifecycle: LifecycleRegistry
        get() = lifecycleRegistry
}

/**
 * Navigation options.
 */
data class NavOptions(
    /**
     * Pop up to this destination before navigating.
     */
    val popUpTo: String? = null,

    /**
     * Whether to include the popUpTo destination in the pop.
     */
    val inclusive: Boolean = false,

    /**
     * Whether to launch as single top (don't add if already on top).
     */
    val launchSingleTop: Boolean = false,

    /**
     * Whether to restore state when navigating back.
     */
    val restoreState: Boolean = false,
) {
    companion object {
        val Default = NavOptions()
    }

    class Builder {
        private var popUpTo: String? = null
        private var inclusive: Boolean = false
        private var launchSingleTop: Boolean = false
        private var restoreState: Boolean = false

        fun popUpTo(route: String, inclusive: Boolean = false): Builder {
            this.popUpTo = route
            this.inclusive = inclusive
            return this
        }

        fun launchSingleTop(value: Boolean = true): Builder {
            this.launchSingleTop = value
            return this
        }

        fun restoreState(value: Boolean = true): Builder {
            this.restoreState = value
            return this
        }

        fun build(): NavOptions = NavOptions(popUpTo, inclusive, launchSingleTop, restoreState)
    }
}

/**
 * Build navigation options.
 */
fun navOptions(builder: NavOptions.Builder.() -> Unit): NavOptions =
    NavOptions.Builder().apply(builder).build()

/**
 * Navigation event listener.
 */
fun interface NavigationListener {
    fun onNavigationEvent(event: NavigationEvent)
}

/**
 * Navigation events.
 */
sealed class NavigationEvent {
    data class Navigate(val entry: NavBackStackEntry) : NavigationEvent()
    data class Pop(val entry: NavBackStackEntry) : NavigationEvent()
}

/**
 * Serializable navigation state.
 */
data class NavigationState(
    val routes: List<String>,
    val currentIndex: Int,
)
