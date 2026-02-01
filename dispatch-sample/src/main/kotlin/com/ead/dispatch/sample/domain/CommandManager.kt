package com.ead.dispatch.sample.domain

import com.ead.dispatch.sample.domain.model.story.WriterMode
import com.ead.dispatch.sample.presentation.commands.CommandAction

class CommandManager {
    private val _data = listOf(
        Command(
            label = "clear",
            description = "Clear chat messages and reset the on-screen history",
        ),
        Command(
            label = "story-summary",
            description = "Read story metadata, style profile, and counts",
        ),
        Command(
            label = "story-info",
            description = "Show raw story info (debug view)",
        ),
        Command(
            label = "characters",
            description = "List character profiles gathered in CHAT mode",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "locations",
            description = "List world locations gathered in CHAT mode",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "arcs",
            description = "List narrative arcs gathered in CHAT mode",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "world-rules",
            description = "List world rules and constraints",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "cultures",
            description = "List cultures and social groups",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "events",
            description = "List historical events",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "organizations",
            description = "List organizations and factions",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "relationships",
            description = "List relationships between entities",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "location-features",
            description = "List location features and landmarks",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "artifacts",
            description = "List artifacts and important items",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "timeline",
            description = "List timeline entries",
            modes = setOf(WriterMode.CHAT),
        ),
        Command(
            label = "volumes",
            description = "List volumes used in STORY mode",
            modes = setOf(WriterMode.CHAT_STORY),
        ),
        Command(
            label = "chapters",
            description = "List chapters used in STORY mode",
            modes = setOf(WriterMode.CHAT_STORY),
        ),
        Command(
            label = "scenes",
            description = "List scenes used in STORY mode",
            modes = setOf(WriterMode.CHAT_STORY),
        )
    )

    val data: List<Command> = _data

    fun commandsFor(writerMode: WriterMode): List<Command> {
        return data.filter { command ->
            command.modes.isEmpty() || command.modes.contains(writerMode)
        }
    }

    fun routing(input: String, writerMode: WriterMode): CommandAction? {
        val command = parseSlashCommand(input) ?: return null
        val allowed = commandsFor(writerMode).any { it.label == command }
        if (!allowed) return null
        return when (command) {
            "clear" -> CommandAction.ClearContext
            "story-summary" -> CommandAction.OpenStorySummary
            "story-info" -> CommandAction.OpenStoryInfo
            "characters" -> CommandAction.OpenEntityList("characters")
            "locations" -> CommandAction.OpenEntityList("locations")
            "arcs" -> CommandAction.OpenEntityList("arcs")
            "world-rules" -> CommandAction.OpenEntityList("world-rules")
            "cultures" -> CommandAction.OpenEntityList("cultures")
            "events" -> CommandAction.OpenEntityList("events")
            "organizations" -> CommandAction.OpenEntityList("organizations")
            "relationships" -> CommandAction.OpenEntityList("relationships")
            "location-features" -> CommandAction.OpenEntityList("location-features")
            "artifacts" -> CommandAction.OpenEntityList("artifacts")
            "timeline" -> CommandAction.OpenEntityList("timeline")
            "volumes" -> CommandAction.OpenEntityList("volumes")
            "chapters" -> CommandAction.OpenEntityList("chapters")
            "scenes" -> CommandAction.OpenEntityList("scenes")
            else -> null
        }
    }

    private fun parseSlashCommand(input: String): String? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) return null
        val command = trimmed.drop(1).takeWhile { !it.isWhitespace() }
        if (command.isBlank()) return null
        return command.lowercase()
    }
}
