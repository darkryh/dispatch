package com.ead.dispatch.sample.domain.agents.context

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.feature.testGraph
import ai.koog.agents.testing.tools.getMockExecutor
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.koog.context.orchestrator.api.ContextNodeStage
import com.ead.koog.context.orchestrator.api.nodeManageContext
import com.ead.koog.context.orchestrator.api.nodeApplyCompactedContext
import com.ead.koog.context.orchestrator.api.nodeManageContextAfterLlm
import com.ead.koog.context.orchestrator.api.nodeManageContextAfterToolLoop
import com.ead.koog.context.orchestrator.api.nodeManageContextBeforeLlm
import com.ead.koog.context.orchestrator.api.nodeManageContextBeforeToolLoop
import com.ead.koog.context.orchestrator.api.nodeManageContextEndTurn
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextOrchestratorNodeGraphDesignTest {

    @Test
    fun `linear llm flow keeps explicit context lifecycle nodes`() {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry = ToolRegistry {}) {
                mockLLMAnswer("ok").asDefaultResponse
            },
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<String, String>("context-linear") {
                val applyCompacted by nodeApplyCompactedContext<String>()
                val contextBeforeLlm by nodeManageContextBeforeLlm<String>()
                val businessNode by node<String, String>("business-node") { it }
                val contextAfterLlm by nodeManageContextAfterLlm<String>()
                val contextEndTurn by nodeManageContextEndTurn<String>()

                edge(nodeStart forwardTo applyCompacted)
                edge(applyCompacted forwardTo contextBeforeLlm)
                edge(contextBeforeLlm forwardTo businessNode)
                edge(businessNode forwardTo contextAfterLlm)
                edge(contextAfterLlm forwardTo contextEndTurn)
                edge(contextEndTurn forwardTo nodeFinish)
            },
            id = "context-linear-graph-test",
        ) {
            testGraph<String, String>("context-linear") {
                val start = startNode()
                val finish = finishNode()
                val apply = assertNodeByName<String, String>("context-apply-compacted")
                val before = assertNodeByName<String, String>("context-before-llm")
                val business = assertNodeByName<String, String>("business-node")
                val after = assertNodeByName<String, String>("context-after-llm")
                val end = assertNodeByName<String, String>("context-end-turn")

                assertReachable(start, apply)
                assertReachable(apply, before)
                assertReachable(before, business)
                assertReachable(business, after)
                assertReachable(after, end)
                assertReachable(end, finish)

                assertEdges {
                    start alwaysGoesTo apply
                    apply alwaysGoesTo before
                    before alwaysGoesTo business
                    business alwaysGoesTo after
                    after alwaysGoesTo end
                    end alwaysGoesTo finish
                }
            }
        }

        // Force graph build and assertion execution.
        agent.toString()
    }

    @Test
    fun `tool loop design supports explicit context nodes around tool stage`() {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry = ToolRegistry {}) {
                mockLLMAnswer("ok").asDefaultResponse
            },
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<String, String>("context-tool-loop") {
                val beforeToolLoop by nodeManageContextBeforeToolLoop<String>()
                val toolLoopNode by node<String, String>("tool-loop-node") { it }
                val afterToolLoop by nodeManageContextAfterToolLoop<String>()

                edge(nodeStart forwardTo beforeToolLoop)
                edge(beforeToolLoop forwardTo toolLoopNode)
                edge(toolLoopNode forwardTo afterToolLoop)
                edge(afterToolLoop forwardTo nodeFinish)
            },
            id = "context-tool-loop-graph-test",
        ) {
            testGraph<String, String>("context-tool-loop") {
                val start = startNode()
                val finish = finishNode()
                val beforeTools = assertNodeByName<String, String>("context-before-tool-loop")
                val tools = assertNodeByName<String, String>("tool-loop-node")
                val afterTools = assertNodeByName<String, String>("context-after-tool-loop")

                assertReachable(start, beforeTools)
                assertReachable(beforeTools, tools)
                assertReachable(tools, afterTools)
                assertReachable(afterTools, finish)

                assertEdges {
                    start alwaysGoesTo beforeTools
                    beforeTools alwaysGoesTo tools
                    tools alwaysGoesTo afterTools
                    afterTools alwaysGoesTo finish
                }
            }
        }

        // Force graph build and assertion execution.
        agent.toString()
    }

    @Test
    fun `generic staged node helper allows custom stage names in graph`() {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry = ToolRegistry {}) {
                mockLLMAnswer("ok").asDefaultResponse
            },
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<String, String>("context-generic-stage") {
                val customBefore by nodeManageContext<String>(
                    stage = ContextNodeStage.BEFORE_LLM,
                    name = "ctx-before-custom",
                )
                val worker by node<String, String>("worker") { it }
                val customEnd by nodeManageContext<String>(
                    stage = ContextNodeStage.END_TURN,
                    name = "ctx-end-custom",
                )

                edge(nodeStart forwardTo customBefore)
                edge(customBefore forwardTo worker)
                edge(worker forwardTo customEnd)
                edge(customEnd forwardTo nodeFinish)
            },
            id = "context-generic-stage-graph-test",
        ) {
            testGraph<String, String>("context-generic-stage") {
                val start = startNode()
                val finish = finishNode()
                val before = assertNodeByName<String, String>("ctx-before-custom")
                val worker = assertNodeByName<String, String>("worker")
                val end = assertNodeByName<String, String>("ctx-end-custom")

                assertReachable(start, before)
                assertReachable(before, worker)
                assertReachable(worker, end)
                assertReachable(end, finish)

                assertEdges {
                    start alwaysGoesTo before
                    before alwaysGoesTo worker
                    worker alwaysGoesTo end
                    end alwaysGoesTo finish
                }
            }
        }

        // Force graph build and assertion execution.
        agent.toString()
    }

    @Test
    fun `graph without context nodes remains free of implicit context nodes`() {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry = ToolRegistry {}) {
                mockLLMAnswer("ok").asDefaultResponse
            },
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<String, Int>("no-context-nodes") {
                val worker by node<String, String>("worker") { it }
                val inspect by node<String, Int>("inspect") {
                    llm.readSession {
                        prompt.messages
                            .filterIsInstance<Message.System>()
                            .count { msg -> msg.content.contains("[COMPACTED MEMORY ARTIFACT]") }
                    }
                }
                edge(nodeStart forwardTo worker)
                edge(worker forwardTo inspect)
                edge(inspect forwardTo nodeFinish)
            },
            id = "no-context-nodes-graph-test",
        ) {
            testGraph<String, Int>("no-context-nodes") {
                val start = startNode()
                val finish = finishNode()
                val worker = assertNodeByName<String, String>("worker")
                val inspect = assertNodeByName<String, Int>("inspect")

                assertReachable(start, worker)
                assertReachable(worker, inspect)
                assertReachable(inspect, finish)

                assertEdges {
                    start alwaysGoesTo worker
                    worker alwaysGoesTo inspect
                    inspect alwaysGoesTo finish
                }
            }
        }

        val compactedMarkerCount = runBlocking { agent.run("hello") }
        assertEquals(0, compactedMarkerCount)
    }

    @Test
    fun `conditional branch routing keeps explicit context nodes and route-specific paths`() {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry = ToolRegistry {}) {
                mockLLMAnswer("ok").asDefaultResponse
            },
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<String, String>("context-conditional-branches") {
                val applyCompacted by nodeApplyCompactedContext<String>()
                val contextBeforeLlm by nodeManageContextBeforeLlm<String>()
                val router by node<String, String>("router") { input ->
                    if (input.startsWith("chat:", ignoreCase = true)) "chat" else "story"
                }
                val chatBranch by node<String, String>("chat-branch") { "chat-branch" }
                val storyBranch by node<String, String>("story-branch") { "story-branch" }

                edge(nodeStart forwardTo applyCompacted)
                edge(applyCompacted forwardTo contextBeforeLlm)
                edge(contextBeforeLlm forwardTo router)
                edge(router forwardTo chatBranch onCondition { it == "chat" })
                edge(router forwardTo storyBranch onCondition { it == "story" })
                edge(chatBranch forwardTo nodeFinish transformed { it })
                edge(storyBranch forwardTo nodeFinish transformed { it })
            },
            id = "context-conditional-branches-test",
        ) {
            testGraph<String, String>("context-conditional-branches") {
                val start = startNode()
                val finish = finishNode()
                val apply = assertNodeByName<String, String>("context-apply-compacted")
                val before = assertNodeByName<String, String>("context-before-llm")
                val router = assertNodeByName<String, String>("router")
                val chatBranch = assertNodeByName<String, String>("chat-branch")
                val storyBranch = assertNodeByName<String, String>("story-branch")

                assertReachable(start, apply)
                assertReachable(apply, before)
                assertReachable(before, router)
                assertReachable(router, chatBranch)
                assertReachable(router, storyBranch)
                assertReachable(chatBranch, finish)
                assertReachable(storyBranch, finish)

                assertEdges {
                    start alwaysGoesTo apply
                    apply alwaysGoesTo before
                    before alwaysGoesTo router
                    router withOutput "chat" goesTo chatBranch
                    router withOutput "story" goesTo storyBranch
                }
            }
        }

        assertEquals("chat-branch", runBlocking { agent.run("chat: hi") })

        val freshAgent = AIAgent(
            promptExecutor = getMockExecutor(toolRegistry = ToolRegistry {}) {
                mockLLMAnswer("ok").asDefaultResponse
            },
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<String, String>("context-conditional-branches-fresh") {
                val applyCompacted by nodeApplyCompactedContext<String>()
                val contextBeforeLlm by nodeManageContextBeforeLlm<String>()
                val router by node<String, String>("router") { input ->
                    if (input.startsWith("chat:", ignoreCase = true)) "chat" else "story"
                }
                val chatBranch by node<String, String>("chat-branch") { "chat-branch" }
                val storyBranch by node<String, String>("story-branch") { "story-branch" }

                edge(nodeStart forwardTo applyCompacted)
                edge(applyCompacted forwardTo contextBeforeLlm)
                edge(contextBeforeLlm forwardTo router)
                edge(router forwardTo chatBranch onCondition { it == "chat" })
                edge(router forwardTo storyBranch onCondition { it == "story" })
                edge(chatBranch forwardTo nodeFinish transformed { it })
                edge(storyBranch forwardTo nodeFinish transformed { it })
            },
            id = "context-conditional-branches-test-fresh",
        )

        assertEquals("story-branch", runBlocking { freshAgent.run("story: hi") })
    }
}
