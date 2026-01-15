package com.ead.dispatch.sample.presentation.story_info

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.navigation.NavController
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.runtime.dispatchScope
import com.ead.dispatch.sample.domain.model.story.StoryChatContext
import com.ead.dispatch.state.getValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.LazyListScope
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun StoryInfoScreen(navController: NavController) {
    val viewModel = viewModel<StoryInfoViewModel>()
    val state by viewModel.state.collectAsState()
    val theme = LocalTheme.current
    val scope = dispatchScope()

    scope.onKeyEvent { event ->
        if (event.key == "Escape") {
            navController.popBackStack()
        }
    }

    fun header(text: String) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(2))
            Text(
                text = text,
                style = theme.primary + TextStyle(bold = true),
            )
        }
    }

    fun labelValue(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(2))
            Text(
                text = label.padEnd(18),
                style = theme.muted,
            )
            Text(
                text = value,
                style = theme.primary,
            )
        }
    }

    fun LazyListScope.section(title: String) {
        item { Spacer(Modifier.height(1)) }
        item { header(title) }
        item { Spacer(Modifier.height(1)) }
    }

    fun LazyListScope.emptyRow(text: String) {
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(text = text, style = theme.muted)
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Spacer(Modifier.height(1)) }
        item { header("Story Info") }
        item { Spacer(Modifier.height(1)) }

        if (state.isLoading) {
            emptyRow("Loading story data...")
            return@LazyColumn
        }

        val error = state.error
        if (error != null) {
            emptyRow("Error: $error")
            return@LazyColumn
        }

        val context = state.context
        if (context == null) {
            emptyRow("No story data found.")
            return@LazyColumn
        }

        item {
            labelValue("Session Id", state.storyId ?: "unknown")
        }

        fun LazyListScope.storySection(context: StoryChatContext) {
            val story = context.story
            section("Story")
            if (story == null) {
                emptyRow("No story record found for this session.")
            } else {
                item { labelValue("Title", story.title ?: "(empty)") }
                item { labelValue("Genre", story.genre ?: "(empty)") }
                item { labelValue("Setting", story.setting ?: "(empty)") }
                item { labelValue("Plot", story.plotOutline ?: "(empty)") }
                item { labelValue("Status", story.status?.name ?: "(empty)") }

                val style = story.styleProfile
                item { labelValue("Logline", style?.logline ?: "(empty)") }
                item { labelValue("Theme", style?.theme ?: "(empty)") }
                item { labelValue("Tone", style?.tone ?: "(empty)") }
                item { labelValue("Stakes", style?.stakes ?: "(empty)") }
                item { labelValue("POV", style?.pov ?: "(empty)") }
                item { labelValue("Tense", style?.tense ?: "(empty)") }
                item { labelValue("Audience", style?.targetAudience ?: "(empty)") }
                item { labelValue("Pacing", style?.pacing ?: "(empty)") }
            }

            section("Characters (${context.characters.size})")
            if (context.characters.isEmpty()) {
                emptyRow("No characters saved.")
            } else {
                items(context.characters) { character ->
                    val label = character.name.ifBlank { "(unnamed)" }
                    val details = buildString {
                        append("id=")
                        append(character.id)
                        character.roles.takeIf { it.isNotEmpty() }?.let { roles ->
                            append(", roles=")
                            append(roles.joinToString())
                        }
                        character.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            append(", desc=")
                            append(desc)
                        }
                    }
                    labelValue(label, details)
                }
            }

            section("Locations (${context.locations.size})")
            if (context.locations.isEmpty()) {
                emptyRow("No locations saved.")
            } else {
                items(context.locations) { location ->
                    val label = location.profile.name.ifBlank { "(unnamed)" }
                    val details = buildString {
                        append("id=")
                        append(location.id)
                        location.profile.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            append(", desc=")
                            append(desc)
                        }
                    }
                    labelValue(label, details)
                }
            }

            section("Arcs (${context.arcs.size})")
            if (context.arcs.isEmpty()) {
                emptyRow("No arcs saved.")
            } else {
                items(context.arcs) { arc ->
                    val label = arc.title.ifBlank { "(untitled)" }
                    val details = buildString {
                        append("id=")
                        append(arc.id)
                        append(", scope=")
                        append(arc.scopeType.name)
                        arc.scopeId?.takeIf { it.isNotBlank() }?.let { scopeId ->
                            append("($scopeId)")
                        }
                    }
                    labelValue(label, details)
                }
            }

            section("Facts (${context.facts.size})")
            if (context.facts.isEmpty()) {
                emptyRow("No facts saved.")
            } else {
                items(context.facts) { fact ->
                    val label = fact.factType.name
                    val details = buildString {
                        append("id=")
                        append(fact.id)
                        append(", content=")
                        append(fact.content)
                    }
                    labelValue(label, details)
                }
            }

            item { Spacer(Modifier.height(1)) }
            emptyRow("Press Esc to go back")
        }

        storySection(context)
    }
}
