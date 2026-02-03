package com.ead.dispatch.sample.domain

import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.domain.model.story.WriterMode
import com.ead.dispatch.sample.presentation.commands.CommandAction

class CommandManager {
    private object CommandLabels {
        const val CLEAR = "clear"
        const val STORY_CHAT = "story-chat"
    }

    private val _data = listOf(
        Command(
            label = CommandLabels.CLEAR,
            description = "Clear chat messages and reset the on-screen history",
        ),
        Command(
            label = CommandLabels.STORY_CHAT,
            description = "Open the story chat dashboard",
            modes = setOf(WriterMode.CHAT, WriterMode.CHAT_STORY),
        ),
    ) + EntityOptionType.all.map { option ->
        Command(
            label = option.id,
            description = option.description,
            modes = option.modes,
        )
    }

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
            CommandLabels.CLEAR -> CommandAction.ClearContext
            CommandLabels.STORY_CHAT -> CommandAction.OpenStoryChat
            else -> EntityOptionType.fromId(command)?.let { CommandAction.OpenEntityList(it) }
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
