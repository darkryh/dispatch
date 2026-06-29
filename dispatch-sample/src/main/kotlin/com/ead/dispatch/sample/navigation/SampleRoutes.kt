package com.ead.dispatch.sample.navigation

import com.ead.dispatch.navigation.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The sample's navigation graph: one [NavKey] per destination.
 *
 * Every route is a `@Serializable data class` so the navigation back stack can save and restore it.
 * The home launcher and the global "go to…" palette both build their entries from
 * [CatalogDestination], so adding a screen there is the only place a new route needs to be wired
 * for navigation UI.
 */
@Serializable
@SerialName("home")
data class HomeRoute(
    /** The launcher card the cursor should rest on when Home is (re)entered. */
    val cursor: Int = 0,
) : NavKey

@Serializable
@SerialName("inputs")
data object InputsRoute : NavKey

@Serializable
@SerialName("buttons")
data object ButtonsRoute : NavKey

@Serializable
@SerialName("lists")
data object ListsRoute : NavKey

@Serializable
@SerialName("tables")
data object TablesRoute : NavKey

@Serializable
@SerialName("hierarchy")
data object HierarchyRoute : NavKey

@Serializable
@SerialName("tasks")
data object TasksRoute : NavKey

@Serializable
@SerialName("progress")
data object ProgressRoute : NavKey

@Serializable
@SerialName("surfaces")
data object SurfacesRoute : NavKey

@Serializable
@SerialName("layout")
data object LayoutRoute : NavKey

@Serializable
@SerialName("review")
data object ReviewRoute : NavKey

/**
 * The streaming-chat screen. Carries a [conversationId] purely to demonstrate reading a typed
 * route argument inside the navigation `entry { }` lambda — the sample only ever uses one id.
 */
@Serializable
@SerialName("chat")
data class ChatRoute(
    val conversationId: String = "sample",
) : NavKey
