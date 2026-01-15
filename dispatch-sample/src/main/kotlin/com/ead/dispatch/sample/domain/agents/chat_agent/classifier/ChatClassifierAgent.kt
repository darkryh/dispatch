package com.ead.dispatch.sample.domain.agents.chat_agent.classifier

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.model.session.Session

class ChatClassifierAgent {

    /*fun create(session: Session) = AIAgent(
        promptExecutor = AIProvider.deepseekPromptExecutor,
        agentConfig = AIAgentConfig(
            prompt = chatClassifierPrompt(
                flashModel = AIProvider.deepseekChatLlmModel.id,
                proModel = AIProvider.deepseekReasonerLlmModel.id,
            ),
            model = AIProvider.deepseekChatLlmModel,
            maxAgentIterations = 50,
        ),
        strategy = strategy<String, StructuredResponse<ChatAIModelClassifierResponse>>("chat-mode.classifier.strategy") {
            val responseStructure = JsonStructure.create<ChatAIModelClassifierResponse>(schemaGenerator = BasicJsonSchemaGenerator)

            val llmCall by nodeLLMRequestStructured(
                config = StructuredRequestConfig(
                    default = StructuredRequest.Manual(responseStructure),
                    fixingParser = StructureFixingParser(
                        model = AIProvider.deepseekReasonerLlmModel,
                        retries = 2
                    )
                )
            )

            edge(nodeStart forwardTo llmCall)
            edge(llmCall forwardTo nodeFinish transformed { result -> result.getOrThrow() })
        },
        id = "${session.id}:classifier",
    )*/
}
