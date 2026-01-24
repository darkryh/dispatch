package com.ead.dispatch.sample.presentation.story_summary

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.navigation.NavController
import com.ead.dispatch.navigation.navigate
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.runtime.dispatchScope
import com.ead.dispatch.sample.navigation.EntityListRoute
import com.ead.dispatch.sample.navigation.StorySummaryRoute
import com.ead.dispatch.state.getValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun StorySummaryScreen(navController: NavController, route: StorySummaryRoute) {
    val theme = LocalTheme.current
    val scope = dispatchScope()
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<StorySummaryViewModel>()
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        val disposeEsc = scope.addKeyEventHandler { event ->
            if (event.key == "Escape" || event.key == "Esc") {
                navController.popBackStack()
            }
        }

        val disposeShortcuts = keyboardInterceptor.register { event ->
            when (event.key.lowercase()) {
                "c" -> {
                    navController.navigate(EntityListRoute(type = "characters", storyId = route.storyId))
                    true
                }
                "l" -> {
                    navController.navigate(EntityListRoute(type = "locations", storyId = route.storyId))
                    true
                }
                "a" -> {
                    navController.navigate(EntityListRoute(type = "arcs", storyId = route.storyId))
                    true
                }
                "w" -> {
                    navController.navigate(EntityListRoute(type = "world-rules", storyId = route.storyId))
                    true
                }
                "v" -> {
                    navController.navigate(EntityListRoute(type = "volumes", storyId = route.storyId))
                    true
                }
                else -> false
            }
        }

        onDispose {
            disposeEsc()
            disposeShortcuts()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(
                    text = "Story Summary",
                    style = theme.primary + TextStyle(bold = true),
                )
            }
        }
        item { Spacer(Modifier.height(1)) }
        if (state.isLoading) {
            item { summaryRow("Status", "Loading story data...") }
            return@LazyColumn
        }

        val error = state.error
        if (error != null) {
            item { summaryRow("Error", error) }
            return@LazyColumn
        }

        val story = state.story
        if (story == null) {
            item { summaryRow("Status", "No story found for this session.") }
            return@LazyColumn
        }

        item { summaryRow("Title", story.title ?: "(empty)") }
        item { summaryRow("Genre", story.genre ?: "(empty)") }
        item { summaryRow("Setting", story.setting ?: "(empty)") }
        item { summaryRow("Status", story.status?.name ?: "(empty)") }

        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text("Style Profile", style = theme.primary + TextStyle(bold = true))
            }
        }
        item { Spacer(Modifier.height(1)) }
        val style = story.styleProfile
        item { summaryRow("Logline", style?.logline ?: "(empty)") }
        item { summaryRow("Tone", style?.tone ?: "(empty)") }
        item { summaryRow("POV", style?.pov ?: "(empty)") }
        item { summaryRow("Tense", style?.tense ?: "(empty)") }
        item { summaryRow("Pacing", style?.pacing ?: "(empty)") }

        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text("Library", style = theme.primary + TextStyle(bold = true))
            }
        }
        item { Spacer(Modifier.height(1)) }
        val counts = state.counts
        item { summaryRow("Characters", "${counts?.characters ?: 0} total · press C") }
        item { summaryRow("Locations", "${counts?.locations ?: 0} total · press L") }
        item { summaryRow("Arcs", "${counts?.arcs ?: 0} total · press A") }
        item { summaryRow("World Rules", "${counts?.worldRules ?: 0} total") }
        item { summaryRow("Cultures", "${counts?.cultures ?: 0} total") }
        item { summaryRow("Events", "${counts?.events ?: 0} total") }
        item { summaryRow("Organizations", "${counts?.organizations ?: 0} total") }
        item { summaryRow("Relationships", "${counts?.relationships ?: 0} total") }
        item { summaryRow("Location Features", "${counts?.locationFeatures ?: 0} total") }
        item { summaryRow("Artifacts", "${counts?.artifacts ?: 0} total") }
        item { summaryRow("Timeline Entries", "${counts?.timelineEntries ?: 0} total") }
        item { summaryRow("Volumes", "${counts?.volumes ?: 0} total · press V") }
        item { summaryRow("Chapters", "${counts?.chapters ?: 0} total") }
        item { summaryRow("Scenes", "${counts?.scenes ?: 0} total") }

        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(
                    text = "Shortcuts: C characters · L locations · A arcs · W world rules · V volumes · Esc back",
                    style = theme.muted,
                )
            }
        }
    }
}

@Dispatchable
private fun summaryRow(label: String, value: String) {
    val theme = LocalTheme.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(2))
        Text("$label:", style = theme.muted)
        Spacer(Modifier.width(2))
        Text(value, style = theme.primary)
    }
}
