package com.ead.dispatch.navigation

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Box
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.LocalSavedStateHandle
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.viewmodel.DefaultViewModelFactory
import com.ead.dispatch.viewmodel.LocalViewModelProvider
import com.ead.dispatch.viewmodel.ViewModelFactory
import kotlinx.serialization.json.Json
import java.net.URLDecoder

/**
 * Navigation host that displays the current destination.
 *
 * Example:
 * ```kotlin
 * val navController = rememberNavController()
 *
 * NavHost(
 *     navController = navController,
 *     startDestination = "home",
 * ) {
 *     screen("home") { HomeScreen(navController) }
 *     screen("settings") { SettingsScreen(navController) }
 *     screen("detail/{id}") { backStackEntry ->
 *         DetailScreen(backStackEntry.requireArgument("id"))
 *     }
 * }
 * ```
 *
 * @param navController The navigation controller.
 * @param startDestination The initial destination route.
 * @param modifier Modifiers to apply.
 * @param builder Builder for defining navigation graph.
 */
@Dispatchable
fun NavHost(
    navController: NavController,
    startDestination: String,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    val composer = Composer.current

    // Build navigation graph
    val navGraph = remember {
        NavGraphBuilder().apply(builder).build()
    }

    // Navigate to start destination if no current destination
    if (navController.currentDestination == null) {
        navController.navigate(startDestination)
    }

    val lastRouteState = remember { mutableStateOf<String?>(null) }
    val screenSlotRange = remember { mutableStateOf<IntRange?>(null) }

    // Render current destination
    val currentEntry = navController.currentDestination
    if (currentEntry != null) {
        if (lastRouteState.value != currentEntry.route) {
            screenSlotRange.value?.let { range ->
                composer.clearSlotsInRange(range.first, range.last + 1)
            }
            lastRouteState.value = currentEntry.route
        }
        val match = navGraph.matchScreen(currentEntry.route)
        if (match != null) {
            val resolvedArguments = resolveArguments(
                entry = currentEntry,
                pattern = match.pattern,
                argumentSpecs = match.definition.arguments,
            )
            val resolvedEntry = navController.updateEntryArguments(currentEntry, resolvedArguments)
            val screenSlotStart = composer.currentPositionKey()
            Box(modifier = modifier) {
                CompositionLocalProvider(
                    LocalNavBackStackEntry provides resolvedEntry,
                    LocalLifecycleOwner provides resolvedEntry,
                    LocalViewModelProvider provides resolvedEntry.viewModelProvider,
                    LocalSavedStateHandle provides resolvedEntry.savedStateHandle,
                ) {
                    match.definition.content(resolvedEntry)
                }
            }
            val screenSlotEnd = composer.currentPositionKey()
            screenSlotRange.value = screenSlotStart until screenSlotEnd
        }
    }
}

/**
 * Navigation host that accepts a typed start destination.
 *
 * @param startDestination The initial destination route object.
 * @param json Serializer configuration for route payloads.
 */
@Dispatchable
fun NavHost(
    navController: NavController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    json: Json = DefaultRouteJson,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = routeForValue(startDestination, json),
        modifier = modifier,
        builder = builder,
    )
}

/**
 * Builder for navigation graph.
 */
class NavGraphBuilder {
    private val screens = mutableMapOf<String, ScreenDefinition>()

    /**
     * Define a screen destination.
     *
     * @param route The route pattern (e.g., "home", "detail/{id}").
     * @param content The composable content for this screen.
     */
    fun screen(
        route: String,
        content: @Dispatchable (NavBackStackEntry) -> Unit,
    ) {
        screens[route] = ScreenDefinition(route, content)
    }

    /**
     * Define a screen with navigation arguments.
     */
    fun screen(
        route: String,
        arguments: List<NavArgument> = emptyList(),
        content: @Dispatchable (NavBackStackEntry) -> Unit,
    ) {
        screens[route] = ScreenDefinition(route, content, arguments)
    }

    internal fun build(): NavGraph = NavGraph(screens.toMap())
}

/**
 * A screen definition in the navigation graph.
 */
internal data class ScreenDefinition(
    val route: String,
    val content: @Dispatchable (NavBackStackEntry) -> Unit,
    val arguments: List<NavArgument> = emptyList(),
)

/**
 * The navigation graph.
 */
internal class NavGraph(
    private val screens: Map<String, ScreenDefinition>,
) {
    /**
     * Find a screen matching the given route.
     */
    fun matchScreen(route: String): ScreenMatch? {
        val normalizedRoute = route.substringBefore("?")
        // Exact match first
        screens[normalizedRoute]?.let { return ScreenMatch(it, normalizedRoute) }

        // Pattern match (e.g., "detail/123" matches "detail/{id}")
        for ((pattern, definition) in screens) {
            val normalizedPattern = pattern.substringBefore("?")
            if (matchesPattern(normalizedRoute, normalizedPattern)) {
                return ScreenMatch(definition, normalizedPattern)
            }
        }

        return null
    }

    private fun matchesPattern(route: String, pattern: String): Boolean {
        val routeParts = route.split("/")
        val patternParts = pattern.split("/")

        if (routeParts.size != patternParts.size) return false

        return routeParts.zip(patternParts).all { (r, p) ->
            r == p || (p.startsWith("{") && p.endsWith("}"))
        }
    }
}

internal data class ScreenMatch(
    val definition: ScreenDefinition,
    val pattern: String,
)

/**
 * Navigation argument definition.
 */
data class NavArgument(
    /**
     * Argument name (matches {name} in route pattern).
     */
    val name: String,

    /**
     * Argument type.
     */
    val type: NavType<*> = NavType.StringType,

    /**
     * Whether the argument is optional.
     */
    val optional: Boolean = false,

    /**
     * Default value if optional.
     */
    val defaultValue: Any? = null,
)

/**
 * Navigation argument types.
 */
sealed class NavType<T> {
    abstract fun parseValue(value: String): T
    abstract fun serializeValue(value: T): String

    object StringType : NavType<String>() {
        override fun parseValue(value: String): String = value
        override fun serializeValue(value: String): String = value
    }

    object IntType : NavType<Int>() {
        override fun parseValue(value: String): Int = value.toInt()
        override fun serializeValue(value: Int): String = value.toString()
    }

    object LongType : NavType<Long>() {
        override fun parseValue(value: String): Long = value.toLong()
        override fun serializeValue(value: Long): String = value.toString()
    }

    object BoolType : NavType<Boolean>() {
        override fun parseValue(value: String): Boolean = value.toBoolean()
        override fun serializeValue(value: Boolean): String = value.toString()
    }
}

private fun resolveArguments(
    entry: NavBackStackEntry,
    pattern: String,
    argumentSpecs: List<NavArgument>,
): Map<String, String> {
    val resolved = entry.arguments.toMutableMap()
    val queryArguments = parseQueryArguments(entry.route)
    val pathArguments = parsePathArguments(entry.route, pattern)

    resolved.putAll(queryArguments)
    resolved.putAll(pathArguments)
    applyArgumentSpecs(resolved, argumentSpecs)

    return resolved
}

private fun parsePathArguments(route: String, pattern: String): Map<String, String> {
    val routeParts = route.substringBefore("?").split("/")
    val patternParts = pattern.substringBefore("?").split("/")
    if (routeParts.size != patternParts.size) return emptyMap()

    val arguments = mutableMapOf<String, String>()
    routeParts.zip(patternParts).forEach { (value, token) ->
        if (token.startsWith("{") && token.endsWith("}")) {
            val key = token.removeSurrounding("{", "}")
            arguments[key] = decodeSegment(value)
        }
    }
    return arguments
}

private fun parseQueryArguments(route: String): Map<String, String> {
    val query = route.substringAfter("?", "")
    if (query.isEmpty()) return emptyMap()

    val arguments = mutableMapOf<String, String>()
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
        arguments[key] = value
    }
    return arguments
}

private fun decodeSegment(value: String): String {
    return runCatching { URLDecoder.decode(value, Charsets.UTF_8) }.getOrElse { value }
}

private fun applyArgumentSpecs(arguments: MutableMap<String, String>, specs: List<NavArgument>) {
    specs.forEach { spec ->
        val name = spec.name
        val current = arguments[name]
        if (current == null) {
            val defaultValue = serializeDefaultValue(spec)
            if (defaultValue != null) {
                arguments[name] = defaultValue
            } else if (!spec.optional) {
                throw IllegalArgumentException("Missing required argument: $name")
            }
        } else {
            try {
                spec.type.parseValue(current)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid value for argument: $name", e)
            }
        }
    }
}

private fun serializeDefaultValue(argument: NavArgument): String? {
    val defaultValue = argument.defaultValue ?: return null
    return when (argument.type) {
        NavType.StringType -> (defaultValue as? String)
            ?: throw IllegalArgumentException("Default value for ${argument.name} must be a String")
        NavType.IntType -> (defaultValue as? Int)?.toString()
            ?: throw IllegalArgumentException("Default value for ${argument.name} must be an Int")
        NavType.LongType -> (defaultValue as? Long)?.toString()
            ?: throw IllegalArgumentException("Default value for ${argument.name} must be a Long")
        NavType.BoolType -> (defaultValue as? Boolean)?.toString()
            ?: throw IllegalArgumentException("Default value for ${argument.name} must be a Boolean")
    }
}

/**
 * Remember a navigation controller.
 */
@Dispatchable
fun rememberNavController(
    viewModelFactory: ViewModelFactory = DefaultViewModelFactory(),
): NavController {
    return remember { NavController(viewModelFactory = viewModelFactory) }
}

/**
 * Helper to create a simple nav argument.
 */
fun stringArg(name: String, optional: Boolean = false, default: String? = null): NavArgument =
    NavArgument(name, NavType.StringType, optional, default)

fun intArg(name: String, optional: Boolean = false, default: Int? = null): NavArgument =
    NavArgument(name, NavType.IntType, optional, default)

fun longArg(name: String, optional: Boolean = false, default: Long? = null): NavArgument =
    NavArgument(name, NavType.LongType, optional, default)

fun boolArg(name: String, optional: Boolean = false, default: Boolean? = null): NavArgument =
    NavArgument(name, NavType.BoolType, optional, default)
