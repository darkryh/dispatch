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

            val characterTotal = context.characters.items.size + context.characters.overflowCount
            section("Characters ($characterTotal)")
            if (context.characters.items.isEmpty()) {
                emptyRow("No characters saved.")
            } else {
                items(context.characters.items) { character ->
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

            val locationTotal = context.locations.items.size + context.locations.overflowCount
            section("Locations ($locationTotal)")
            if (context.locations.items.isEmpty()) {
                emptyRow("No locations saved.")
            } else {
                items(context.locations.items) { location ->
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

            val arcTotal = context.arcs.items.size + context.arcs.overflowCount
            section("Arcs ($arcTotal)")
            if (context.arcs.items.isEmpty()) {
                emptyRow("No arcs saved.")
            } else {
                items(context.arcs.items) { arc ->
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

            val worldRuleTotal = context.worldRules.items.size + context.worldRules.overflowCount
            section("World Rules ($worldRuleTotal)")
            if (context.worldRules.items.isEmpty()) {
                emptyRow("No world rules saved.")
            } else {
                items(context.worldRules.items) { rule ->
                    val details = buildString {
                        append("id=")
                        append(rule.id)
                        rule.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            append(", desc=")
                            append(desc)
                        }
                    }
                    labelValue(rule.title, details)
                }
            }

            val cultureTotal = context.cultures.items.size + context.cultures.overflowCount
            section("Cultures ($cultureTotal)")
            if (context.cultures.items.isEmpty()) {
                emptyRow("No cultures saved.")
            } else {
                items(context.cultures.items) { culture ->
                    val details = buildString {
                        append("id=")
                        append(culture.id)
                        culture.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            append(", desc=")
                            append(desc)
                        }
                    }
                    labelValue(culture.name, details)
                }
            }

            val eventTotal = context.events.items.size + context.events.overflowCount
            section("Events ($eventTotal)")
            if (context.events.items.isEmpty()) {
                emptyRow("No events saved.")
            } else {
                items(context.events.items) { event ->
                    val details = buildString {
                        append("id=")
                        append(event.id)
                        event.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            append(", desc=")
                            append(desc)
                        }
                    }
                    labelValue(event.name, details)
                }
            }

            val organizationTotal = context.organizations.items.size + context.organizations.overflowCount
            section("Organizations ($organizationTotal)")
            if (context.organizations.items.isEmpty()) {
                emptyRow("No organizations saved.")
            } else {
                items(context.organizations.items) { org ->
                    val details = buildString {
                        append("id=")
                        append(org.id)
                        org.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            append(", desc=")
                            append(desc)
                        }
                    }
                    labelValue(org.name, details)
                }
            }

            val relationshipTotal = context.relationships.items.size + context.relationships.overflowCount
            section("Relationships ($relationshipTotal)")
            if (context.relationships.items.isEmpty()) {
                emptyRow("No relationships saved.")
            } else {
                items(context.relationships.items) { rel ->
                    val details = buildString {
                        append("id=")
                        append(rel.id)
                        append(", ")
                        append(rel.subjectType)
                        append("=")
                        append(rel.subjectId)
                        append(" -> ")
                        append(rel.objectType)
                        append("=")
                        append(rel.objectId)
                        rel.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                            append(", notes=")
                            append(notes)
                        }
                    }
                    labelValue(rel.relation, details)
                }
            }

            val locationFeatureTotal = context.locationFeatures.items.size + context.locationFeatures.overflowCount
            section("Location Features ($locationFeatureTotal)")
            if (context.locationFeatures.items.isEmpty()) {
                emptyRow("No location features saved.")
            } else {
                items(context.locationFeatures.items) { feature ->
                    val details = buildString {
                        append("id=")
                        append(feature.id)
                        feature.locationId?.let { locId ->
                            append(", location=")
                            append(locId)
                        }
                        feature.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            append(", desc=")
                            append(desc)
                        }
                    }
                    labelValue(feature.name, details)
                }
            }

            val artifactTotal = context.artifacts.items.size + context.artifacts.overflowCount
            section("Artifacts ($artifactTotal)")
            if (context.artifacts.items.isEmpty()) {
                emptyRow("No artifacts saved.")
            } else {
                items(context.artifacts.items) { artifact ->
                    val details = buildString {
                        append("id=")
                        append(artifact.id)
                        artifact.ownerId?.let { owner ->
                            append(", owner=")
                            append(owner)
                        }
                        artifact.locationId?.let { locId ->
                            append(", location=")
                            append(locId)
                        }
                        artifact.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            append(", desc=")
                            append(desc)
                        }
                    }
                    labelValue(artifact.name, details)
                }
            }

            val timelineTotal = context.timelineEntries.items.size + context.timelineEntries.overflowCount
            section("Timeline Entries ($timelineTotal)")
            if (context.timelineEntries.items.isEmpty()) {
                emptyRow("No timeline entries saved.")
            } else {
                items(context.timelineEntries.items) { entry ->
                    val details = buildString {
                        append("id=")
                        append(entry.id)
                        append(", order=")
                        append(entry.orderIndex)
                        entry.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            append(", desc=")
                            append(desc)
                        }
                    }
                    labelValue(entry.title, details)
                }
            }

            item { Spacer(Modifier.height(1)) }
            emptyRow("Press Esc to go back")
        }

        storySection(context)
    }
}
