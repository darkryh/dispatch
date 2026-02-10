package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolPolicyTest {

    @Test
    fun `detects write tools by prefix`() {
        assertTrue(isWriteToolName("createCharacter"))
        assertTrue(isWriteToolName("updateStory"))
        assertTrue(isWriteToolName("deleteArc"))
        assertFalse(isWriteToolName("listCharacters"))
    }

    @Test
    fun `detects destructive tools`() {
        assertTrue(isDestructiveToolName("deleteCharacter"))
        assertTrue(isDestructiveToolName("clearContext"))
        assertFalse(isDestructiveToolName("updateCharacter"))
    }

    @Test
    fun `detects decision tool names`() {
        assertTrue(isDecisionToolName("requestUserChoice"))
        assertTrue(isDecisionToolName("askUserChoice"))
        assertFalse(isDecisionToolName("createCharacter"))
    }

    @Test
    fun `write tools are disabled when turn policy disallows write`() {
        val policy = ChatTurnPolicy(
            intentClass = ChatIntentClass.CREATIVE,
            decisionPath = ChatDecisionPath.DIRECT_RESPONSE,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            rationale = "creative turn",
            fromDecisionPrompt = false,
        )

        assertFalse(isToolAllowedForTurn(policy, "createCharacter"))
        assertTrue(isToolAllowedForTurn(policy, "getChatContext"))
        assertTrue(isToolAllowedForTurn(policy, "requestUserChoice"))
    }

    @Test
    fun `selector-gated destructive turns allow decision tool but block write tools`() {
        val policy = ChatTurnPolicy(
            intentClass = ChatIntentClass.DESTRUCTIVE,
            decisionPath = ChatDecisionPath.SELECTOR,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = true,
            rationale = "destructive turn",
            fromDecisionPrompt = false,
        )

        assertTrue(isToolAllowedForTurn(policy, "requestUserChoice"))
        assertFalse(isToolAllowedForTurn(policy, "deleteCharacter"))
        assertFalse(isToolAllowedForTurn(policy, "updateCharacter"))
    }
}
