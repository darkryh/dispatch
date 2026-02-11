package com.ead.dispatch.sample.domain.agents.chat_agent.node

import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatDecisionPath
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatIntentClass
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnInput
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryPreferenceNodesPolicyTest {

    @Test
    fun `load preferences only for direct response and direct write`() {
        assertTrue(shouldLoadUserPreferences(inputWith(decisionPath = ChatDecisionPath.DIRECT_RESPONSE)))
        assertTrue(shouldLoadUserPreferences(inputWith(decisionPath = ChatDecisionPath.DIRECT_WRITE)))
        assertFalse(shouldLoadUserPreferences(inputWith(decisionPath = ChatDecisionPath.FOLLOW_UP)))
        assertFalse(shouldLoadUserPreferences(inputWith(decisionPath = ChatDecisionPath.SELECTOR)))
        assertFalse(
            shouldLoadUserPreferences(
                inputWith(
                    decisionPath = ChatDecisionPath.DIRECT_WRITE,
                    fromDecisionPrompt = true,
                ),
            ),
        )
    }

    @Test
    fun `preference save skip reason covers all guarded branches`() {
        assertEquals(
            "from_decision_prompt",
            preferenceSaveSkipReason(
                policy = policyWith(
                    fromDecisionPrompt = true,
                    shouldSavePreference = true,
                    preferenceConceptKeywords = listOf("writer_tense_preference"),
                    preferenceConfidence = 0.99,
                    requestTextHash = "h1",
                ),
                previousSavedHash = null,
            ),
        )

        assertEquals(
            "classifier_not_recommended",
            preferenceSaveSkipReason(
                policy = policyWith(
                    shouldSavePreference = false,
                    preferenceConceptKeywords = listOf("writer_tense_preference"),
                    preferenceConfidence = 0.99,
                    requestTextHash = "h1",
                ),
                previousSavedHash = null,
            ),
        )

        assertEquals(
            "low_confidence",
            preferenceSaveSkipReason(
                policy = policyWith(
                    shouldSavePreference = true,
                    preferenceConceptKeywords = listOf("writer_tense_preference"),
                    preferenceConfidence = preferenceSaveConfidenceThreshold - 0.01,
                    requestTextHash = "h1",
                ),
                previousSavedHash = null,
            ),
        )

        assertEquals(
            "no_target_concepts",
            preferenceSaveSkipReason(
                policy = policyWith(
                    shouldSavePreference = true,
                    preferenceConceptKeywords = emptyList(),
                    preferenceConfidence = 0.99,
                    requestTextHash = "h1",
                ),
                previousSavedHash = null,
            ),
        )

        assertEquals(
            "missing_request_hash",
            preferenceSaveSkipReason(
                policy = policyWith(
                    shouldSavePreference = true,
                    preferenceConceptKeywords = listOf("writer_tense_preference"),
                    preferenceConfidence = 0.99,
                    requestTextHash = "",
                ),
                previousSavedHash = null,
            ),
        )

        assertEquals(
            "duplicate_message_hash",
            preferenceSaveSkipReason(
                policy = policyWith(
                    shouldSavePreference = true,
                    preferenceConceptKeywords = listOf("writer_tense_preference"),
                    preferenceConfidence = 0.99,
                    requestTextHash = "same",
                ),
                previousSavedHash = "same",
            ),
        )

        assertEquals(
            null,
            preferenceSaveSkipReason(
                policy = policyWith(
                    shouldSavePreference = true,
                    preferenceConceptKeywords = listOf("writer_tense_preference"),
                    preferenceConfidence = 0.99,
                    requestTextHash = "new-hash",
                ),
                previousSavedHash = "old-hash",
            ),
        )
    }

    @Test
    fun `resolve preference concepts filters unknown and normalizes values`() {
        val resolved = resolvePreferenceConcepts(
            listOf(
                "writer_tense_preference",
                "WRITER_TENSE_PREFERENCE",
                " writer_pov_preference ",
                "unknown",
                "",
            ),
        )

        assertEquals(
            listOf("writer_tense_preference", "writer_pov_preference"),
            resolved.map { it.keyword },
        )
    }

    private fun inputWith(
        decisionPath: ChatDecisionPath,
        fromDecisionPrompt: Boolean = false,
    ): ChatTurnInput = ChatTurnInput(
        request = ChatRequest(
            text = "test",
            storyId = "s1",
            fromDecisionPrompt = fromDecisionPrompt,
        ),
        policy = policyWith(
            decisionPath = decisionPath,
            fromDecisionPrompt = fromDecisionPrompt,
        ),
    )

    private fun policyWith(
        decisionPath: ChatDecisionPath = ChatDecisionPath.DIRECT_RESPONSE,
        fromDecisionPrompt: Boolean = false,
        shouldSavePreference: Boolean = false,
        preferenceConceptKeywords: List<String> = emptyList(),
        preferenceConfidence: Double = 0.0,
        requestTextHash: String = "hash",
    ): ChatTurnPolicy = ChatTurnPolicy(
        intentClass = ChatIntentClass.CREATIVE,
        decisionPath = decisionPath,
        explicitWriteIntent = false,
        allowWriteTools = decisionPath == ChatDecisionPath.DIRECT_WRITE,
        requireSelectorForDestructive = decisionPath == ChatDecisionPath.SELECTOR,
        rationale = "test policy",
        fromDecisionPrompt = fromDecisionPrompt,
        requestTextHash = requestTextHash,
        shouldSavePreference = shouldSavePreference,
        preferenceConceptKeywords = preferenceConceptKeywords,
        preferenceConfidence = preferenceConfidence,
        preferenceEvidenceSpan = "",
        preferenceReasoning = "",
    )
}
