package io.github.darkryh.dispatch.navigation

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.navigation.harness.ReliabilityHarness
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.widget.LazyColumn
import io.github.darkryh.dispatch.widget.Panel
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.TextField
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

                    val lines =
                        harness.render(theme = theme, boundedHeight = true) {
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
    data class Editor(
        val revision: Int,
    ) : ReliabilityRoute

    @Serializable
    data object Library : ReliabilityRoute

    @Serializable
    data class Inspector(
        val entityId: String,
    ) : ReliabilityRoute

    fun anchor(): String =
        when (this) {
            Home -> "anchor::route_home"
            is Editor -> "anchor::route_editor"
            Library -> "anchor::route_library"
            is Inspector -> "anchor::route_inspector"
        }
}

@Composable
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

@Composable
private fun ReliabilityHomeRoute() {
    Panel(modifier = Modifier.fillMaxWidth(), title = "Home") {
        Text("anchor::route_home")
        Text("overview metrics")
        Text("queues healthy")
    }
}

@Composable
private fun ReliabilityEditorRoute(revision: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::route_editor")
        Text("revision=$revision")
        TextField(
            value = "compose scene revision $revision with tighter beats",
            onValueChange = {},
            icon = "> ",
            placeholder = "editor input",
            onSubmit = {},
        )
    }
}

@Composable
private fun ReliabilityLibraryRoute() {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Text("anchor::route_library") }
        items(List(90) { "library-item-${it + 1}" }) { line ->
            Text(line)
        }
    }
}

@Composable
private fun ReliabilityInspectorRoute(entityId: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("anchor::route_inspector")
        Text("entity=$entityId")
        Text("state=consistent")
    }
}
