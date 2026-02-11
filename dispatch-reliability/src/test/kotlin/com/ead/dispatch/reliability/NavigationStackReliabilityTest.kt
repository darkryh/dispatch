package com.ead.dispatch.reliability

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.navigation.NavBackStack
import com.ead.dispatch.navigation.NavDisplay
import com.ead.dispatch.navigation.NavEntry
import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.navigation.navigate
import com.ead.dispatch.navigation.popBackStack
import com.ead.dispatch.reliability.harness.ReliabilityHarness
import com.ead.dispatch.theme.DispatchTheme
import com.ead.dispatch.widget.InputTextField
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.Text
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertTrue

class NavigationStackReliabilityTest {
    @Test
    fun `navigation churn across routes keeps active route anchor visible`() {
        val sizes = listOf(80 to 24, 120 to 40)
        val themes = listOf(DispatchTheme.Dark, DispatchTheme.Light, DispatchTheme.Minimal)

        sizes.forEach { (width, height) ->
            themes.forEach { theme ->
                val harness = ReliabilityHarness(width = width, height = height, theme = theme)
                val backStack = NavBackStack<ReliabilityRoute>(ReliabilityRoute.Home)

                repeat(320) { step ->
                    when (step % 5) {
                        0 -> backStack.navigate(ReliabilityRoute.Editor(step))
                        1 -> backStack.navigate(ReliabilityRoute.Library)
                        2 -> backStack.navigate(ReliabilityRoute.Inspector("entity-${step % 11}"))
                        else -> backStack.popBackStack()
                    }

                    val lines = harness.render(theme = theme, boundedHeight = true) {
                        ReliabilityNavSurface(backStack = backStack)
                    }

                    val expectedAnchor = backStack.last().anchor()
                    assertTrue(
                        lines.any { it.contains(expectedAnchor) },
                        "missing anchor $expectedAnchor at $width x $height step=$step",
                    )
                }
            }
        }
    }
}

@Serializable
private sealed interface ReliabilityRoute : NavKey {
    @Serializable
    data object Home : ReliabilityRoute

    @Serializable
    data class Editor(val revision: Int) : ReliabilityRoute

    @Serializable
    data object Library : ReliabilityRoute

    @Serializable
    data class Inspector(val entityId: String) : ReliabilityRoute

    fun anchor(): String =
        when (this) {
            Home -> "anchor::route_home"
            is Editor -> "anchor::route_editor"
            Library -> "anchor::route_library"
            is Inspector -> "anchor::route_inspector"
        }
}

@Dispatchable
private fun ReliabilityNavSurface(backStack: NavBackStack<ReliabilityRoute>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        NavDisplay(
            backStack = backStack,
            entryProvider = { key ->
                NavEntry(key = key) { route ->
                    when (route) {
                        ReliabilityRoute.Home -> ReliabilityHomeRoute()
                        is ReliabilityRoute.Editor -> ReliabilityEditorRoute(route.revision)
                        ReliabilityRoute.Library -> ReliabilityLibraryRoute()
                        is ReliabilityRoute.Inspector -> ReliabilityInspectorRoute(route.entityId)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("anchor::nav_footer")
            Spacer(modifier = Modifier.weight(1f))
            Text("backstack=${backStack.size}")
        }
    }
}

@Dispatchable
private fun ReliabilityHomeRoute() {
    Panel(modifier = Modifier.fillMaxWidth(), title = "Home") {
        Text("anchor::route_home")
        Text("overview metrics")
        Text("queues healthy")
    }
}

@Dispatchable
private fun ReliabilityEditorRoute(revision: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::route_editor")
        Text("revision=$revision")
        InputTextField(
            value = "compose scene revision $revision with tighter beats",
            onValueChange = {},
            icon = "> ",
            placeholder = "editor input",
            onSubmit = {},
        )
    }
}

@Dispatchable
private fun ReliabilityLibraryRoute() {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Text("anchor::route_library") }
        items(List(90) { "library-item-${it + 1}" }) { line ->
            Text(line)
        }
    }
}

@Dispatchable
private fun ReliabilityInspectorRoute(entityId: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::route_inspector")
        Text("entity=$entityId")
        Text("state=consistent")
    }
}
