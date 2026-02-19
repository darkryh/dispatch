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

    @Test
    fun `creative selector-gated turns block story write tools but allow decision tool`() {
        val policy = StoryTurnPolicy(
            intentClass = StoryIntentClass.CREATIVE,
            decisionPath = StoryDecisionPath.SELECTOR,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = true,
            rationale = "creative branching turn",
            fromDecisionPrompt = false,
            requestTextHash = "x",
        )

        assertTrue(isStoryToolAllowedForTurn(policy, "requestUserChoice"))
        assertFalse(isStoryToolAllowedForTurn(policy, "createChapter"))
        assertFalse(isStoryToolAllowedForTurn(policy, "applyChapterDraftProposal"))
    }

    @Test
    fun `execute creative write with required creative choice uses selector path`() {
        val policy = buildStoryTurnPolicy(
            request = StoryRequest(
                text = "Create several chapter direction options and apply one.",
                storyId = "s1",
            ),
            intentSignal = StoryIntentSignal(
                intentClass = StoryIntentClass.CREATIVE,
                explicitWriteIntent = true,
                confidence = 0.88,
                evidenceSpan = "several chapter direction options",
                reasoning = "High-impact branching request.",
                resolvedAction = com.ead.dispatch.sample.domain.agents.intent.IntentResolvedAction.WRITE_CREATE,
                requiresCreativeChoice = true,
                executionIntent = IntentExecutionIntent.EXECUTE,
            ),
        )

        assertEquals(StoryDecisionPath.SELECTOR, policy.decisionPath)
        assertTrue(policy.allowWriteTools)
        assertTrue(policy.requireSelectorForCreative)
        assertFalse(policy.requireSelectorForDestructive)
    }

}
