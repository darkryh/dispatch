package com.ead.dispatch.sample.navigation

import com.ead.dispatch.navigation.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("home")
data class HomeRoute(
    val selectedCard: Int = 0,
) : NavKey

@Serializable
@SerialName("chat")
data class ChatRoute(
    val conversationId: String = "sample",
) : NavKey

@Serializable
@SerialName("components")
data class ComponentsRoute(
    val section: String = "controls",
) : NavKey

object ComponentSections {
    const val CONTROLS = "controls"
    const val SURFACES = "surfaces"
    const val DATA = "data"
    const val PROGRESS = "progress"
    const val LISTS = "lists"
    const val REVIEW = "review"
    const val WORKFLOW = "workflow"

    val all = listOf(CONTROLS, SURFACES, DATA, PROGRESS, LISTS, REVIEW, WORKFLOW)
}
