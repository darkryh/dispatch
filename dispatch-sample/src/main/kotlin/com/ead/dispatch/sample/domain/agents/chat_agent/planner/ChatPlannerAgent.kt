package com.ead.dispatch.sample.domain.agents.chat_agent.planner

class ChatPlannerAgent {

    /*fun create(session: Session, promptExecutor: PromptExecutor, model: LLModel): AIAgent<String, StructuredResponse<ChatPlannerResponse>> {
        val agentConfig = AIAgentConfig(
            prompt = chatPlannerPrompt(),
            model = model,
            maxAgentIterations = 5,
        )

        return AIAgent(
            promptExecutor = promptExecutor,
            strategy = strategy<String, StructuredResponse<ChatPlannerResponse>>("chat-mode.planner") {
                val responseStructure = JsonStructure.create<ChatPlannerResponse>(
                    schemaGenerator = BasicJsonSchemaGenerator
                )

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
            agentConfig = agentConfig,
            id = "${session.id}:planner",
        ) {
            install(Persistence) {
                storage = Storage.provider
                enableAutomaticPersistence = true
                rollbackStrategy = RollbackStrategy.MessageHistoryOnly
            }
        )
    }*/
}
