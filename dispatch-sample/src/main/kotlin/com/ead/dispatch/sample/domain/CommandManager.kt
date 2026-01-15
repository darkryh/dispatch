package com.ead.dispatch.sample.domain

import com.ead.dispatch.sample.presentation.commands.CommandAction

class CommandManager {
    private val _data = listOf(
        Command(
            label = "help",
            description = "More info, about commands and options"
        ),
        Command(
            label = "clear",
            description = "Clear conversation history and free up context"
        ),
        Command(
            label = "character",
            description = "Open the character screen"
        ),
        Command(
            label = "story-info",
            description = "Open the story info screen"
        )
    )

    val data: List<Command> = _data

    fun routing(input: String): CommandAction? {
        val command = parseSlashCommand(input) ?: return null
        return when (command) {
            "help" -> CommandAction.ShowHelp
            "clear" -> CommandAction.ClearContext
            "character" -> CommandAction.OpenCharacter
            "character-manual" -> CommandAction.OpenCharacter
            "character-ai" -> CommandAction.OpenCharacter
            "story-info" -> CommandAction.OpenStoryInfo
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
