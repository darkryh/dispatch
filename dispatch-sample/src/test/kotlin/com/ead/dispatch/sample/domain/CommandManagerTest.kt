package com.ead.dispatch.sample.domain

import com.ead.dispatch.sample.domain.export.StoryExportFormat
import com.ead.dispatch.sample.domain.export.StoryExportOutputTarget
import com.ead.dispatch.sample.domain.export.StoryExportScopeType
import com.ead.dispatch.sample.domain.model.story.WriterMode
import com.ead.dispatch.sample.presentation.commands.CommandAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandManagerTest {

    private val manager = CommandManager()

    @Test
    fun `export command parses long options`() {
        val action = manager.routing(
            input = "/export --scope volume --id vol-1 --format txt --out downloads",
            writerMode = WriterMode.CHAT_STORY,
        )

        val export = assertIs<CommandAction.ExportStory>(action)
        assertEquals(StoryExportScopeType.VOLUME, export.scopeType)
        assertEquals("vol-1", export.scopeId)
        assertEquals(StoryExportFormat.TXT, export.format)
        assertEquals(StoryExportOutputTarget.DOWNLOADS, export.outputTarget)
    }

    @Test
    fun `export command parses positional scope and id`() {
        val action = manager.routing(
            input = "/export chapter ch-22 --format md",
            writerMode = WriterMode.CHAT_STORY,
        )

        val export = assertIs<CommandAction.ExportStory>(action)
        assertEquals(StoryExportScopeType.CHAPTER, export.scopeType)
        assertEquals("ch-22", export.scopeId)
        assertEquals(StoryExportFormat.MD, export.format)
        assertEquals(StoryExportOutputTarget.CWD, export.outputTarget)
    }

    @Test
    fun `export command in wrong mode is not routed`() {
        val action = manager.routing(
            input = "/export --scope story",
            writerMode = WriterMode.CHAT,
        )

        assertEquals(null, action)
    }

    @Test
    fun `export command reports missing scope id for chapter`() {
        val action = manager.routing(
            input = "/export --scope chapter",
            writerMode = WriterMode.CHAT_STORY,
        )

        assertIs<CommandAction.ShowError>(action)
    }
}
