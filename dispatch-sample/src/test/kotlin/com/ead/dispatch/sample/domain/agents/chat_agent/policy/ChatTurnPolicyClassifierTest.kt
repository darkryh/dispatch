package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatTurnPolicyClassifierTest {

    @Test
    fun `creative intent uses direct response without write tools`() {
        val request = ChatRequest(
            text = "Give me three ideas for a rival faction.",
            storyId = "s1",
        )
        val policy = buildTurnPolicy(request, ChatIntentClass.CREATIVE)

        assertEquals(ChatDecisionPath.DIRECT_RESPONSE, policy.decisionPath)
        assertFalse(policy.allowWriteTools)
    }

    @Test
    fun `decision prompt responses are treated as write continuation`() {
        val request = ChatRequest(
            text = "Replace existing",
            storyId = "s1",
            fromDecisionPrompt = true,
        )

        val policy = buildTurnPolicy(request, ChatIntentClass.CREATIVE)

        assertEquals(ChatDecisionPath.DIRECT_WRITE, policy.decisionPath)
        assertTrue(policy.allowWriteTools)
        assertTrue(policy.explicitWriteIntent)
        assertEquals(ChatIntentClass.WRITE, policy.intentClass)
    }

    @Test
    fun `write intent allows direct write`() {
        val request = ChatRequest(
            text = "Create a character named Kael.",
            storyId = "s1",
        )

        val policy = buildTurnPolicy(request, ChatIntentClass.WRITE)

        assertEquals(ChatDecisionPath.DIRECT_WRITE, policy.decisionPath)
        assertTrue(policy.allowWriteTools)
        assertFalse(policy.requireSelectorForDestructive)
    }

    @Test
    fun `destructive intent requires selector path`() {
        val request = ChatRequest(
            text = "Delete the old location record.",
            storyId = "s1",
        )

        val policy = buildTurnPolicy(request, ChatIntentClass.DESTRUCTIVE)

        assertEquals(ChatDecisionPath.SELECTOR, policy.decisionPath)
        assertTrue(policy.allowWriteTools)
        assertTrue(policy.requireSelectorForDestructive)
    }

    @Test
    fun `ambiguous intent asks follow up and blocks write tools`() {
        val request = ChatRequest(
            text = "Update it with a darker tone.",
            storyId = "s1",
        )

        val policy = buildTurnPolicy(request, ChatIntentClass.AMBIGUOUS)

        assertEquals(ChatDecisionPath.FOLLOW_UP, policy.decisionPath)
        assertFalse(policy.allowWriteTools)
    }

    @Test
    fun `ambiguous class with explicit write high confidence enables write`() {
        val request = ChatRequest(
            text = "create five characters, you can do it on your own",
            storyId = "s1",
        )

        val policy = buildTurnPolicy(
            request = request,
            intentSignal = ChatIntentSignal(
                intentClass = ChatIntentClass.AMBIGUOUS,
                explicitWriteIntent = true,
                confidence = 0.82,
                evidenceSpan = "create five characters",
                reasoning = "Clear mutate-now phrasing despite informal wording.",
            ),
        )

        assertEquals(ChatDecisionPath.DIRECT_WRITE, policy.decisionPath)
        assertTrue(policy.allowWriteTools)
        assertTrue(policy.explicitWriteIntent)
        assertEquals(ChatIntentClass.WRITE, policy.intentClass)
    }

    @Test
    fun `explicit write with low confidence still asks follow up`() {
        val request = ChatRequest(
            text = "maybe update something",
            storyId = "s1",
        )

        val policy = buildTurnPolicy(
            request = request,
            intentSignal = ChatIntentSignal(
                intentClass = ChatIntentClass.AMBIGUOUS,
                explicitWriteIntent = true,
                confidence = 0.32,
                evidenceSpan = "maybe update",
                reasoning = "Too uncertain to mutate safely.",
            ),
        )

        assertEquals(ChatDecisionPath.FOLLOW_UP, policy.decisionPath)
        assertFalse(policy.allowWriteTools)
    }

    @Test
    fun `inquiry intent does not execute writes even if write signal is high confidence`() {
        val request = ChatRequest(
            text = "Can you create a character like this archetype?",
            storyId = "s1",
        )

        val policy = buildTurnPolicy(
            request = request,
            intentSignal = ChatIntentSignal(
                intentClass = ChatIntentClass.WRITE,
                explicitWriteIntent = true,
                confidence = 0.95,
                evidenceSpan = "create a character",
                reasoning = "Write-capable action identified, but user asks capability question.",
                executionIntent = IntentExecutionIntent.INQUIRE,
            ),
        )

        assertEquals(ChatDecisionPath.DIRECT_RESPONSE, policy.decisionPath)
        assertFalse(policy.allowWriteTools)
        assertFalse(policy.explicitWriteIntent)
    }

    @Test
    fun `execute creative create with required creative choice uses selector before write`() {
        val request = ChatRequest(
            text = "Create multiple new cast directions for this arc and apply one.",
            storyId = "s1",
        )

        val policy = buildTurnPolicy(
            request = request,
            intentSignal = ChatIntentSignal(
                intentClass = ChatIntentClass.CREATIVE,
                explicitWriteIntent = true,
                confidence = 0.89,
                evidenceSpan = "multiple cast directions",
                reasoning = "High-impact branch with multiple valid paths.",
                resolvedAction = com.ead.dispatch.sample.domain.agents.intent.IntentResolvedAction.WRITE_CREATE,
                executionIntent = IntentExecutionIntent.EXECUTE,
                requiresCreativeChoice = true,
            ),
        )

        assertEquals(ChatDecisionPath.SELECTOR, policy.decisionPath)
        assertTrue(policy.allowWriteTools)
        assertFalse(policy.requireSelectorForDestructive)
        assertTrue(policy.requireSelectorForCreative)
    }

    @Test
    fun `preference save signal propagates into policy`() {
        val request = ChatRequest(
            text = "I prefer first-person present tense and sparse prose.",
            storyId = "s1",
        )

        val policy = buildTurnPolicy(
            request = request,
            intentSignal = ChatIntentSignal(
                intentClass = ChatIntentClass.CREATIVE,
                explicitWriteIntent = false,
                confidence = 0.91,
                evidenceSpan = "I prefer",
                reasoning = "Preference statement",
                shouldSavePreference = true,
                preferenceConceptKeywords = listOf(
                    "selector_character_direction_preference",
                    "selector_tone_direction_preference",
                    "selector_general_creative_preference",
                ),
                preferenceConfidenceBand = IntentConfidenceBand.HIGH,
                preferenceNovelty = ChatPreferenceNovelty.NEW,
            ),
        )

        assertTrue(policy.shouldSavePreference)
        assertEquals(
            listOf("selector_character_direction_preference", "selector_tone_direction_preference", "selector_general_creative_preference"),
            policy.preferenceConceptKeywords,
        )
        assertEquals(IntentConfidenceBand.HIGH, policy.preferenceConfidenceBand)
        assertEquals(ChatPreferenceNovelty.NEW, policy.preferenceNovelty)
    }

    @Test
    fun `decision prompt path preserves classifier preference signal`() {
        val request = ChatRequest(
            text = "yes proceed",
            storyId = "s1",
            fromDecisionPrompt = true,
        )

        val policy = buildTurnPolicy(
            request = request,
            intentSignal = ChatIntentSignal(
                intentClass = ChatIntentClass.CREATIVE,
                shouldSavePreference = true,
                preferenceConceptKeywords = listOf("selector_tone_direction_preference"),
                preferenceConfidenceBand = IntentConfidenceBand.HIGH,
                preferenceNovelty = ChatPreferenceNovelty.NEW,
            ),
        )

        assertEquals(ChatDecisionPath.DIRECT_WRITE, policy.decisionPath)
        assertTrue(policy.shouldSavePreference)
        assertEquals(listOf("selector_tone_direction_preference"), policy.preferenceConceptKeywords)
    }
}
