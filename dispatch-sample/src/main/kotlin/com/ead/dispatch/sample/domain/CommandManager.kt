package com.ead.dispatch.sample.domain

import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.domain.export.StoryExportFormat
import com.ead.dispatch.sample.domain.export.StoryExportOutputTarget
import com.ead.dispatch.sample.domain.export.StoryExportScopeType
import com.ead.dispatch.sample.domain.model.story.WriterMode
import com.ead.dispatch.sample.presentation.commands.CommandAction

class CommandManager {
    private object CommandLabels {
        const val CLEAR = "clear"
        const val STORY_CHAT = "story"
        const val EXPORT = "export"
    }

    private data class ParsedSlashCommand(
        val command: String,
        val arguments: List<String>,
    )

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
        Command(
            label = CommandLabels.EXPORT,
            description = "Export story content. Example: /export --scope story --format md --out cwd",
            modes = setOf(WriterMode.CHAT_STORY),
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
        val parsed = parseSlashCommand(input) ?: return null
        val allowed = commandsFor(writerMode).any { it.label == parsed.command }
        if (!allowed) return null
        return when (parsed.command) {
            CommandLabels.CLEAR -> CommandAction.ClearContext
            CommandLabels.STORY_CHAT -> CommandAction.OpenStoryChat
            CommandLabels.EXPORT -> parseExportAction(parsed.arguments)
            else -> EntityOptionType.fromId(parsed.command)?.let { CommandAction.OpenEntityList(it) }
        }
    }

    private fun parseSlashCommand(input: String): ParsedSlashCommand? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) return null
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val rawCommand = tokens.firstOrNull()?.removePrefix("/")?.trim().orEmpty()
        if (rawCommand.isBlank()) return null
        return ParsedSlashCommand(
            command = rawCommand.lowercase(),
            arguments = tokens.drop(1),
        )
    }

    private fun parseExportAction(arguments: List<String>): CommandAction {
        return runCatching {
            var scopeType = StoryExportScopeType.STORY
            var scopeId: String? = null
            var format = StoryExportFormat.MD
            var outputTarget = StoryExportOutputTarget.CWD
            var outputPath: String? = null
            val positional = mutableListOf<String>()

            var index = 0
            while (index < arguments.size) {
                val token = arguments[index]
                when {
                    token.startsWith("--scope=") -> {
                        scopeType = parseScope(token.substringAfter("="))
                    }
                    token == "--scope" -> {
                        val value = arguments.getOrNull(index + 1)
                            ?: error("Missing value for --scope. Use story, volume, or chapter.")
                        scopeType = parseScope(value)
                        index++
                    }
                    token.startsWith("--id=") -> {
                        scopeId = token.substringAfter("=").trim().takeIf { it.isNotBlank() }
                    }
                    token == "--id" -> {
                        scopeId = arguments.getOrNull(index + 1)?.trim()?.takeIf { it.isNotBlank() }
                            ?: error("Missing value for --id.")
                        index++
                    }
                    token.startsWith("--format=") -> {
                        format = parseFormat(token.substringAfter("="))
                    }
                    token == "--format" -> {
                        val value = arguments.getOrNull(index + 1)
                            ?: error("Missing value for --format. Use md or txt.")
                        format = parseFormat(value)
                        index++
                    }
                    token.startsWith("--out=") -> {
                        val parsed = parseOutput(token.substringAfter("="))
                        outputTarget = parsed.first
                        outputPath = parsed.second
                    }
                    token == "--out" -> {
                        val value = arguments.getOrNull(index + 1)
                            ?: error("Missing value for --out. Use cwd, downloads, or a path.")
                        val parsed = parseOutput(value)
                        outputTarget = parsed.first
                        outputPath = parsed.second
                        index++
                    }
                    token.startsWith("--") -> {
                        error("Unknown option '$token' for /export.")
                    }
                    else -> positional += token
                }
                index++
            }

            if (positional.isNotEmpty()) {
                val maybeScope = parseScopeOrNull(positional.first())
                if (maybeScope != null) {
                    scopeType = maybeScope
                    positional.removeAt(0)
                }
            }

            if (scopeType != StoryExportScopeType.STORY && scopeId.isNullOrBlank()) {
                scopeId = positional.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                if (scopeId != null) {
                    positional.removeAt(0)
                }
            }

            if (positional.isNotEmpty()) {
                error("Unexpected arguments for /export: ${positional.joinToString(" ")}")
            }

            if (scopeType != StoryExportScopeType.STORY && scopeId.isNullOrBlank()) {
                error("Scope '${scopeType.name.lowercase()}' requires --id <value>.")
            }

            CommandAction.ExportStory(
                scopeType = scopeType,
                scopeId = scopeId,
                format = format,
                outputTarget = outputTarget,
                outputPath = outputPath,
            )
        }.getOrElse { throwable ->
            CommandAction.ShowError(
                throwable.message
                    ?: "Invalid /export command. Example: /export --scope story --format md --out cwd",
            )
        }
    }

    private fun parseScope(raw: String): StoryExportScopeType {
        return parseScopeOrNull(raw)
            ?: error("Invalid scope '$raw'. Use story, volume, or chapter.")
    }

    private fun parseScopeOrNull(raw: String): StoryExportScopeType? {
        return when (raw.trim().lowercase()) {
            "story" -> StoryExportScopeType.STORY
            "volume" -> StoryExportScopeType.VOLUME
            "chapter" -> StoryExportScopeType.CHAPTER
            else -> null
        }
    }

    private fun parseFormat(raw: String): StoryExportFormat {
        return when (raw.trim().lowercase()) {
            "md", "markdown" -> StoryExportFormat.MD
            "txt", "text" -> StoryExportFormat.TXT
            else -> error("Invalid format '$raw'. Use md or txt.")
        }
    }

    private fun parseOutput(raw: String): Pair<StoryExportOutputTarget, String?> {
        val cleaned = raw.trim()
        return when (cleaned.lowercase()) {
            "cwd" -> StoryExportOutputTarget.CWD to null
            "downloads" -> StoryExportOutputTarget.DOWNLOADS to null
            else -> StoryExportOutputTarget.PATH to cleaned
        }
    }
}
