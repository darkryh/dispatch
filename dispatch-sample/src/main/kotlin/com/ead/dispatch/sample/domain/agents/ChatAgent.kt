package com.ead.dispatch.sample.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatInputRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.classifier.ChatClassifierResponse
import com.ead.dispatch.sample.domain.agents.chat_agent.classifier.chatClassifierPrompt
import com.ead.dispatch.sample.domain.model.story.StoryChatContext
import com.ead.dispatch.sample.domain.model.session.Session

class ChatAgent {

    suspend fun run(session: Session, input: ChatInputRequest): String {
        val agent = AIAgent<ChatInputRequest, String>(
            systemPrompt = "You are chatting Agent Assistant",
            promptExecutor = AIProvider.deepseekPromptExecutor,
            llmModel = AIProvider.deepseekChatLlmModel,
            strategy = strategy<ChatInputRequest, String>("chat-mode.planner") {

                val classifierRequest by nodeClassifyingLlmModelRequest()
                val r2Act = reActStrategy(reasoningInterval = 1)

                edge(nodeStart forwardTo classifierRequest)
                edge(classifierRequest forwardTo r2Act transformed { data -> data.getOrThrow().data.reasoning } )
                edge(r2Act forwardTo nodeFinish)
            },
            id = "${session.id}:chat-agent"
        )

        return agent.run(input)
    }

    @AIAgentBuilderDslMarker
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeClassifyingLlmModelRequest(
        name: String? = null
    ): AIAgentNodeDelegate<ChatInputRequest, Pair<ChatInputRequest, Result<StructuredResponse<ChatClassifierResponse>>>> =
        node(name) { input ->
            classifierAgentRun(
                model = AIProvider.deepseekChatLlmModel,
                chatInputRequest = input
            )
        }

    private suspend fun AIAgentContext.classifierAgentRun(
        model: LLModel,
        chatInputRequest: ChatInputRequest
    ): Pair<ChatInputRequest, Result<StructuredResponse<ChatClassifierResponse>>> = llm.writeSession {
        this.model = model

        rewritePrompt {
            chatClassifierPrompt(
                flashModel = AIProvider.deepseekChatLlmModel.id,
                proModel = AIProvider.deepseekReasonerLlmModel.id,
                promptScope = {
                    user(chatInputRequest.text)
                }
            )
        }

        Pair(
            first = chatInputRequest,
            second = requestLLMStructured<ChatClassifierResponse>(
                fixingParser = StructureFixingParser(
                    model = AIProvider.deepseekChatLlmModel,
                    retries = 2
                )
            )
        )
    }

    @AIAgentBuilderDslMarker
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeLoadingChatContext(
        name: String? = null
    ): AIAgentNodeDelegate<ChatInputRequest, Pair<ChatInputRequest, String>> =
        node(name) { input ->
            loadChatContext(input)
        }

    private suspend fun AIAgentContext.loadChatContext(
        chatInputRequest: ChatInputRequest
    ): Pair<ChatInputRequest, String> {
        val sessionId = agentId.substringBefore(":")
        val context = TODO("Load StoryChatContext for sessionId=$sessionId")
        val prompt = buildContextPrompt(context, chatInputRequest.text)
        injectChatContextPrompt(prompt)
        return Pair(chatInputRequest, prompt)
    }

    private suspend fun AIAgentContext.injectChatContextPrompt(promptBody: String) {
        llm.writeSession {
            rewritePrompt { oldPrompt ->
                prompt("chat-mode-context") {
                    oldPrompt.messages.filterIsInstance<Message.System>().forEach { message(it) }
                    user {
                        markdown {
                            +promptBody
                        }
                    }
                }
            }
        }
    }

    private fun buildContextPrompt(context: StoryChatContext, userMessage: String): String {
        fun <T> List<T>.limit(limit: Int): Pair<List<T>, Int> =
            take(limit) to (size - limit).coerceAtLeast(0)

        val missing = buildList {
            if (context.story?.title.isNullOrBlank()) add("story.title")
            if (context.story?.genre.isNullOrBlank()) add("story.genre")
            if (context.story?.setting.isNullOrBlank()) add("story.setting")
            if (context.story?.plotOutline.isNullOrBlank()) add("story.plot_outline")
            if (context.characters.isEmpty()) add("characters")
            if (context.locations.isEmpty()) add("locations")
        }

        val (characterList, characterExtra) = context.characters.limit(8)
        val (locationList, locationExtra) = context.locations.limit(8)
        val (arcList, arcExtra) = context.arcs.limit(6)
        val (factList, factExtra) = context.facts.limit(6)

        return markdown {
            h2("Story Context (chat mode)")
            +"Title: ${context.story?.title ?: "unknown"}"
            br()
            +"Genre: ${context.story?.genre ?: "unknown"}"
            br()
            +"Setting: ${context.story?.setting ?: "unknown"}"
            br()
            +"Plot Outline: ${context.story?.plotOutline ?: "unknown"}"
            br()
            +"Status: ${context.story?.status?.name ?: "unknown"}"
            br()

            h3("Characters")
            if (characterList.isEmpty()) {
                +"(none)"
            } else {
                bulleted {
                    characterList.forEach { character ->
                        val roles = character.roles.takeIf { it.isNotEmpty() }?.joinToString(", ")
                        item("${character.name} (id=${character.id}, roles=${roles ?: "unspecified"})")
                    }
                    if (characterExtra > 0) item("(+${characterExtra} more)")
                }
            }
            br()

            h3("Locations")
            if (locationList.isEmpty()) {
                +"(none)"
            } else {
                bulleted {
                    locationList.forEach { location ->
                        item("${location.profile.name} (id=${location.id})")
                    }
                    if (locationExtra > 0) item("(+${locationExtra} more)")
                }
            }
            br()

            h3("Arcs")
            if (arcList.isEmpty()) {
                +"(none)"
            } else {
                bulleted {
                    arcList.forEach { arc ->
                        item("${arc.title} (scope=${arc.scopeType.name}, id=${arc.id})")
                    }
                    if (arcExtra > 0) item("(+${arcExtra} more)")
                }
            }
            br()

            h3("Facts")
            if (factList.isEmpty()) {
                +"(none)"
            } else {
                bulleted {
                    factList.forEach { fact ->
                        item("${fact.factType.name}: ${fact.content}")
                    }
                    if (factExtra > 0) item("(+${factExtra} more)")
                }
            }
            br()

            h3("Missing Essentials")
            if (missing.isEmpty()) {
                +"(none)"
            } else {
                bulleted { missing.forEach { item(it) } }
            }
            br()

            h2("User Request")
            +userMessage.trim()
        }
    }

    private suspend fun getLatestExecutorCheckpointId(sessionId: String): String? {
        val agentId = "${sessionId}:chat-agent"
        return Storage.provider.getLatestCheckpoint(agentId)?.checkpointId
    }
}
