package com.ead.dispatch.sample.presentation.chat_mode.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DecisionPromptPayloadParserTest {

    @Test
    fun `parses user_choice payload`() {
        val payload = parseDecisionPromptPayload(
            toolName = "requestUserChoice",
            rawContent = """
                {
                  "type": "user_choice",
                  "promptId": "rel_1",
                  "question": "Which relationship outcome do you want?",
                  "options": [
                    {"id": "A", "label": "Keep existing"},
                    {"id": "B", "label": "Replace with new"},
                    {"id": "C", "label": "Merge both"}
                  ],
                  "customPlaceholder": "tell the assistant how it should proceed..."
                }
            """.trimIndent(),
        )

        assertNotNull(payload)
        assertEquals("rel_1", payload.promptId)
        assertEquals("Which relationship outcome do you want?", payload.question)
        assertEquals(
            listOf("Keep existing", "Replace with new", "Merge both"),
            payload.options.map { it.label },
        )
        assertEquals("tell the assistant how it should proceed...", payload.placeholder)
    }

    @Test
    fun `parses tool request shape without explicit type`() {
        val payload = parseDecisionPromptPayload(
            toolName = "requestUserChoice",
            rawContent = """
                {
                  "question": "Pick one",
                  "options": ["A one", "B two", "C three"]
                }
            """.trimIndent(),
        )

        assertNotNull(payload)
        assertEquals("Pick one", payload.question)
        assertEquals(listOf("A one", "B two", "C three"), payload.options.map { it.label })
    }

    @Test
    fun `parses success envelope from tool result payload`() {
        val payload = parseDecisionPromptPayload(
            toolName = "requestUserChoice",
            rawContent = """
                {
                  "type": "success",
                  "data": {
                    "entity": "STORY",
                    "storyId": "s1",
                    "entityId": "s1",
                    "summary": "User choice required.",
                    "payload": {
                      "type": "user_choice",
                      "promptId": "choice_42",
                      "question": "Which one?",
                      "options": [
                        {"id": "A", "label": "Alpha"},
                        {"id": "B", "label": "Beta"}
                      ],
                      "customPlaceholder": "tell the assistant how it should proceed..."
                    }
                  },
                  "message": "User choice required.",
                  "warnings": []
                }
            """.trimIndent(),
        )

        assertNotNull(payload)
        assertEquals("choice_42", payload.promptId)
        assertEquals("Which one?", payload.question)
        assertEquals(listOf("Alpha", "Beta"), payload.options.map { it.label })
    }

    @Test
    fun `ignores non decision tool payload`() {
        val payload = parseDecisionPromptPayload(
            toolName = "createCharacter",
            rawContent = """
                {
                  "request": {
                    "name": "Dark",
                    "description": "test"
                  }
                }
            """.trimIndent(),
        )

        assertNull(payload)
    }
}
