package com.ead.dispatch.sample.domain.agents.story_agent.policy

import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `inquiry intent in story mode blocks write execution`() {
        val policy = buildStoryTurnPolicy(
            request = StoryRequest(
                text = "Can you create a chapter outline in this style?",
                storyId = "s1",
            ),
            intentSignal = StoryIntentSignal(
                intentClass = StoryIntentClass.WRITE,
                explicitWriteIntent = true,
                confidence = 0.92,
                evidenceSpan = "create a chapter outline",
                reasoning = "Write action understood but this is capability inquiry.",
                executionIntent = IntentExecutionIntent.INQUIRE,
            ),
        )

        assertEquals(StoryDecisionPath.DIRECT_RESPONSE, policy.decisionPath)
        assertFalse(policy.allowWriteTools)
        assertFalse(policy.explicitWriteIntent)
    }

}
