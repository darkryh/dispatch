package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.feature.testGraph
import ai.koog.agents.testing.tools.getMockExecutor
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeApplyTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeClassifyIntent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatTurnPolicyGraphTest {

    @Test
    fun `chat policy planner graph preserves expected node routing`() = runBlocking {
        val agent = createGraphTestAgent(
            mockExecutor = getMockExecutor(
                toolRegistry = ToolRegistry {},
            ) {
                mockLLMAnswer(
                    """
                    {
                      "intent_class": "WRITE",
                      "explicit_write_intent": true,
                      "confidence": 0.95,
                      "evidence_span": "create a character",
                      "reasoning": "explicit write request",
                      "should_save_preference": false,
                      "preference_concepts": [],
                      "preference_confidence": 0.0,
                      "preference_evidence_span": "",
                      "preference_reasoning": "",
                      "execution_intent": "EXECUTE"
                    }
                    """.trimIndent(),
                ).asDefaultResponse
            },
            agentId = "chat-policy-graph-routing-test",
        )

        val result = agent.run(
            ChatRequest(
                text = "create a character",
                storyId = "story-routing-test",
            ),
        )

        assertTrue(result.policy.allowWriteTools)
        assertEquals(ChatDecisionPath.DIRECT_WRITE, result.policy.decisionPath)
    }

    @Test
    fun `creative intent remains direct response and blocks write tools`() = runBlocking {
        val agent = createGraphTestAgent(
            mockExecutor = mockClassifierExecutor(
                intentClass = "CREATIVE",
                explicitWriteIntent = false,
                confidence = 0.92,
                evidenceSpan = "give me ideas",
                reasoning = "ideation only",
            ),
            agentId = "chat-policy-creative-route-test",
        )

        val result = agent.run(
            ChatRequest(
                text = "give me ideas for a location",
                storyId = "story-creative-route-test",
            ),
        )

        assertEquals(ChatDecisionPath.DIRECT_RESPONSE, result.policy.decisionPath)
        assertFalse(result.policy.allowWriteTools)
        assertFalse(result.policy.explicitWriteIntent)
    }

    @Test
    fun `ambiguous low confidence intent routes to follow up`() = runBlocking {
        val agent = createGraphTestAgent(
            mockExecutor = mockClassifierExecutor(
                intentClass = "AMBIGUOUS",
                explicitWriteIntent = true,
                confidence = 0.25,
                evidenceSpan = "maybe update",
                reasoning = "uncertain mutate request",
            ),
            agentId = "chat-policy-follow-up-route-test",
        )

        val result = agent.run(
            ChatRequest(
                text = "maybe update something",
                storyId = "story-follow-up-route-test",
            ),
        )

        assertEquals(ChatDecisionPath.FOLLOW_UP, result.policy.decisionPath)
        assertFalse(result.policy.allowWriteTools)
    }

    @Test
    fun `destructive intent requires selector route`() = runBlocking {
        val agent = createGraphTestAgent(
            mockExecutor = mockClassifierExecutor(
                intentClass = "DESTRUCTIVE",
                explicitWriteIntent = true,
                confidence = 0.98,
                evidenceSpan = "delete this character",
                reasoning = "explicit delete request",
            ),
            agentId = "chat-policy-selector-route-test",
        )

        val result = agent.run(
            ChatRequest(
                text = "delete this character",
                storyId = "story-selector-route-test",
            ),
        )

        assertEquals(ChatDecisionPath.SELECTOR, result.policy.decisionPath)
        assertTrue(result.policy.allowWriteTools)
        assertTrue(result.policy.requireSelectorForDestructive)
    }

    @Test
    fun `decision prompt bypasses classifier and continues write flow`() = runBlocking {
        val agent = createGraphTestAgent(
            mockExecutor = mockClassifierExecutor(
                intentClass = "CREATIVE",
                explicitWriteIntent = false,
                confidence = 0.99,
                evidenceSpan = "irrelevant because bypass",
                reasoning = "should not be used for decision prompt",
                shouldSavePreference = true,
                preferenceConcepts = listOf("writer_tone_like_preference"),
                preferenceConfidence = 0.99,
            ),
            agentId = "chat-policy-decision-prompt-bypass-test",
        )

        val result = agent.run(
            ChatRequest(
                text = "yes, proceed",
                storyId = "story-decision-prompt-route-test",
                fromDecisionPrompt = true,
            ),
        )

        assertEquals(ChatDecisionPath.DIRECT_WRITE, result.policy.decisionPath)
        assertTrue(result.policy.allowWriteTools)
        assertTrue(result.policy.fromDecisionPrompt)
        assertFalse(result.policy.shouldSavePreference)
    }

    @Test
    fun `invalid classifier output falls back to follow up policy`() = runBlocking {
        val agent = createGraphTestAgent(
            mockExecutor = getMockExecutor(
                toolRegistry = ToolRegistry {},
            ) {
                mockLLMAnswer("not-json").asDefaultResponse
            },
            agentId = "chat-policy-classifier-fallback-test",
        )

        val result = agent.run(
            ChatRequest(
                text = "do that",
                storyId = "story-classifier-fallback-test",
            ),
        )

        assertEquals(ChatDecisionPath.FOLLOW_UP, result.policy.decisionPath)
        assertFalse(result.policy.allowWriteTools)
    }

    @Test
    fun `classifier preference signal maps into turn policy in mocked run`() = runBlocking {
        val agent = createGraphTestAgent(
            mockExecutor = mockClassifierExecutor(
                intentClass = "CREATIVE",
                explicitWriteIntent = false,
                confidence = 0.89,
                evidenceSpan = "I prefer first-person present tense",
                reasoning = "user provided stable writing preferences",
                shouldSavePreference = true,
                preferenceConcepts = listOf(
                    "writer_pov_preference",
                    "writer_tense_preference",
                    "unknown_concept_should_be_filtered",
                ),
                preferenceConfidence = 0.87,
                preferenceEvidenceSpan = "first-person present tense",
                preferenceReasoning = "durable preference statement",
            ),
            agentId = "chat-policy-preference-signal-test",
        )

        val result = agent.run(
            ChatRequest(
                text = "I prefer first-person present tense and calm tone",
                storyId = "story-preference-test",
            ),
        )

        assertEquals(ChatDecisionPath.DIRECT_RESPONSE, result.policy.decisionPath)
        assertFalse(result.policy.allowWriteTools)
        assertTrue(result.policy.shouldSavePreference)
        assertEquals(
            listOf("writer_pov_preference", "writer_tense_preference"),
            result.policy.preferenceConceptKeywords,
        )
        assertTrue(result.policy.preferenceConfidence >= 0.87)
    }

    private fun mockClassifierExecutor(
        intentClass: String,
        explicitWriteIntent: Boolean,
        confidence: Double,
        evidenceSpan: String,
        reasoning: String,
        shouldSavePreference: Boolean = false,
        preferenceConcepts: List<String> = emptyList(),
        preferenceConfidence: Double = 0.0,
        preferenceEvidenceSpan: String = "",
        preferenceReasoning: String = "",
        executionIntent: String = "EXECUTE",
    ): ai.koog.prompt.executor.model.PromptExecutor =
        getMockExecutor(
            toolRegistry = ToolRegistry {},
        ) {
            val conceptsJson = preferenceConcepts.joinToString(
                separator = ", ",
                prefix = "[",
                postfix = "]",
            ) { "\"$it\"" }
            mockLLMAnswer(
                """
                {
                  "intent_class": "$intentClass",
                  "explicit_write_intent": $explicitWriteIntent,
                  "confidence": $confidence,
                  "evidence_span": "$evidenceSpan",
                  "reasoning": "$reasoning",
                  "should_save_preference": $shouldSavePreference,
                  "preference_concepts": $conceptsJson,
                  "preference_confidence": $preferenceConfidence,
                  "preference_evidence_span": "$preferenceEvidenceSpan",
                  "preference_reasoning": "$preferenceReasoning",
                  "execution_intent": "$executionIntent"
                }
                """.trimIndent(),
            ).asDefaultResponse
        }

    private fun createGraphTestAgent(
        mockExecutor: ai.koog.prompt.executor.model.PromptExecutor,
        agentId: String,
    ): AIAgent<ChatRequest, ChatTurnInput> =
        AIAgent(
            promptExecutor = mockExecutor,
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<ChatRequest, ChatTurnInput>("chat-mode.planner") {
                val classifyIntent by nodeClassifyIntent()
                val applyTurnPolicy by nodeApplyTurnPolicy()

                edge(nodeStart forwardTo classifyIntent)
                edge(classifyIntent forwardTo applyTurnPolicy)
                edge(applyTurnPolicy forwardTo nodeFinish)
            },
            id = agentId,
        ) {
            testGraph<ChatRequest, ChatTurnInput>("chat-mode.planner") {
                val start = startNode()
                val finish = finishNode()
                val classifyIntent = assertNodeByName<ChatRequest, ClassifiedChatTurn>("classify-intent")
                val applyTurnPolicy = assertNodeByName<ClassifiedChatTurn, ChatTurnInput>("apply-turn-policy")

                assertReachable(start, classifyIntent)
                assertReachable(classifyIntent, applyTurnPolicy)
                assertReachable(applyTurnPolicy, finish)

                assertEdges {
                    start alwaysGoesTo classifyIntent
                    classifyIntent alwaysGoesTo applyTurnPolicy
                    applyTurnPolicy alwaysGoesTo finish
                }
            }
        }
}
