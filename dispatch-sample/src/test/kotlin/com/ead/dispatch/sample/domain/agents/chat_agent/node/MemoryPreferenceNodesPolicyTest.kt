package com.ead.dispatch.sample.domain.agents.chat_agent.node

import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatDecisionContext
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
        assertTrue(shouldLoadChatPreferences(inputWith(decisionPath = ChatDecisionPath.DIRECT_RESPONSE)))
        assertTrue(shouldLoadChatPreferences(inputWith(decisionPath = ChatDecisionPath.DIRECT_WRITE)))
        assertFalse(shouldLoadChatPreferences(inputWith(decisionPath = ChatDecisionPath.FOLLOW_UP)))
        assertFalse(shouldLoadChatPreferences(inputWith(decisionPath = ChatDecisionPath.SELECTOR)))
        assertFalse(
            shouldLoadChatPreferences(
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
            "chat_non_selector_preference_save_disabled",
            preferenceSaveSkipReason(
                policy = policyWith(
                    fromDecisionPrompt = false,
                    requestTextHash = "h1",
                ),
                request = selectorRequest(),
                previousSavedHash = null,
            ),
        )

        assertEquals(
            "missing_decision_context",
            preferenceSaveSkipReason(
                policy = policyWith(
                    fromDecisionPrompt = true,
                    requestTextHash = "h1",
                ),
                request = ChatRequest(text = "x", storyId = "s1", fromDecisionPrompt = true, decisionContext = null),
                previousSavedHash = null,
            ),
        )

        assertEquals(
            null,
            preferenceSaveSkipReason(
                policy = policyWith(
                    fromDecisionPrompt = true,
                    requestTextHash = "h1",
                ),
                request = selectorRequest(),
                previousSavedHash = null,
            ),
        )

        assertEquals(
            "missing_request_hash",
            preferenceSaveSkipReason(
                policy = policyWith(
                    fromDecisionPrompt = true,
                    requestTextHash = "",
                ),
                request = selectorRequest(),
                previousSavedHash = null,
            ),
        )

        assertEquals(
            "duplicate_message_hash",
            preferenceSaveSkipReason(
                policy = policyWith(
                    fromDecisionPrompt = true,
                    requestTextHash = "same",
                ),
                request = selectorRequest(),
                previousSavedHash = "same",
            ),
        )

        assertEquals(
            null,
            preferenceSaveSkipReason(
                policy = policyWith(
                    fromDecisionPrompt = true,
                    requestTextHash = "new-hash",
                ),
                request = selectorRequest(),
                previousSavedHash = "old-hash",
            ),
        )
    }

    @Test
    fun `preference save skip reason allows selector decision context without rule checks`() {
        assertEquals(
            null,
            preferenceSaveSkipReason(
                policy = policyWith(fromDecisionPrompt = true, requestTextHash = "hash-2"),
                request = selectorRequest(
                    question = "free form",
                    optionLabels = emptyList(),
                    selectedValue = "custom direction",
                ),
                previousSavedHash = null,
            ),
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
    )

    private fun selectorRequest(
        question: String = "Pick a title direction",
        optionLabels: List<String> = listOf("Character-focused", "Atmospheric"),
        selectedValue: String = "Character-focused",
    ): ChatRequest = ChatRequest(
        text = selectedValue,
        storyId = "s1",
        fromDecisionPrompt = true,
        decisionContext = ChatDecisionContext(
            promptId = "p1",
            question = question,
            optionLabels = optionLabels,
            selectedValue = selectedValue,
            isCustomSelection = false,
        ),
    )
}
