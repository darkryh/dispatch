package com.ead.dispatch.sample.domain.agents.story_agent.policy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryToolPolicyTest {

    @Test
    fun `detects story write tools by prefix including apply and rollback`() {
        assertTrue(isStoryWriteToolName("setChapterDraft"))
        assertTrue(isStoryWriteToolName("applyChapterDraftProposal"))
        assertTrue(isStoryWriteToolName("rollbackChapterDraft"))
        assertFalse(isStoryWriteToolName("getChapterDraft"))
        assertFalse(isStoryWriteToolName("proposeChapterDraftEdit"))
    }

    @Test
    fun `selector-gated turns block write tools but allow decision tool`() {
        val policy = StoryTurnPolicy(
            intentClass = StoryIntentClass.DESTRUCTIVE,
            decisionPath = StoryDecisionPath.SELECTOR,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = true,
            rationale = "destructive turn",
            fromDecisionPrompt = false,
            requestTextHash = "x",
        )

        assertTrue(isStoryToolAllowedForTurn(policy, "requestUserChoice"))
        assertFalse(isStoryToolAllowedForTurn(policy, "applyChapterDraftProposal"))
        assertFalse(isStoryToolAllowedForTurn(policy, "rollbackChapterDraft"))
    }
}
