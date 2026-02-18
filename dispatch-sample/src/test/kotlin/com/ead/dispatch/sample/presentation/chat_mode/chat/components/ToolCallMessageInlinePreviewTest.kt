package com.ead.dispatch.sample.presentation.chat_mode.chat.components

import com.ead.dispatch.sample.domain.model.message.CliMessage
import com.ead.dispatch.sample.domain.model.message.CliMessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ToolCallMessageInlinePreviewTest {

    @Test
    fun `extracts inline diff from success payload base and candidate text`() {
        val message = CliMessage(
            role = CliMessageRole.TOOL,
            toolName = "proposeChapterDraftEdit",
            data = """
                {
                  "type":"success",
                  "data":{
                    "payload":{
                      "chapterId":"ch-1",
                      "baseText":"Old line",
                      "candidateText":"New line"
                    }
                  }
                }
            """.trimIndent(),
        )

        val preview = parseInlineDiffPreview(message)
        assertNotNull(preview)
        assertEquals("Old line", preview.beforeText)
        assertEquals("New line", preview.afterText)
        assertEquals("chapter:ch-1", preview.fileLabel)
        assertEquals("Change proposal", preview.title)
    }

    @Test
    fun `extracts inline diff from request operations text when payload is not wrapped`() {
        val message = CliMessage(
            role = CliMessageRole.TOOL,
            toolName = "proposeChapterDraftEdit",
            data = """
                {
                  "request":{
                    "chapterId":"ch-2",
                    "operations":[
                      {"type":"REPLACE","text":"Proposed draft text here"}
                    ]
                  }
                }
            """.trimIndent(),
        )

        val preview = parseInlineDiffPreview(message)
        assertNotNull(preview)
        assertEquals("", preview.beforeText)
        assertEquals("Proposed draft text here", preview.afterText)
        assertEquals("chapter:ch-2", preview.fileLabel)
        assertEquals("Change proposal", preview.title)
    }

    @Test
    fun `extracts inline diff from success data request operations when payload is absent`() {
        val message = CliMessage(
            role = CliMessageRole.TOOL,
            toolName = "setChapterDraft",
            data = """
                {
                  "type":"success",
                  "data":{
                    "chapterId":"ch-3",
                    "request":{
                      "operations":[
                        {"type":"REPLACE","text":"Final draft body from data.request"}
                      ]
                    }
                  }
                }
            """.trimIndent(),
        )

        val preview = parseInlineDiffPreview(message)
        assertNotNull(preview)
        assertEquals("", preview.beforeText)
        assertEquals("Final draft body from data.request", preview.afterText)
        assertEquals("chapter:ch-3", preview.fileLabel)
        assertEquals("Draft update preview", preview.title)
    }

    @Test
    fun `returns null when no diff-candidate text fields are present`() {
        val message = CliMessage(
            role = CliMessageRole.TOOL,
            toolName = "createCharacter",
            data = """
                {
                  "type":"success",
                  "data":{
                    "entity":"CHARACTER",
                    "summary":"Character created"
                  }
                }
            """.trimIndent(),
        )

        val preview = parseInlineDiffPreview(message)
        assertNull(preview)
    }
}
