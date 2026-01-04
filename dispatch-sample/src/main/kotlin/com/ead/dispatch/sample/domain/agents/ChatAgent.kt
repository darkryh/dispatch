package com.ead.dispatch.sample.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.providers.LocalFileMemoryProvider
import ai.koog.agents.memory.providers.LocalMemoryConfig
import ai.koog.agents.memory.storage.Aes256GCMEncryptor
import ai.koog.agents.memory.storage.EncryptedStorage
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.message.Message
import ai.koog.rag.base.files.JVMFileSystemProvider
import com.ead.dispatch.sample.domain.Pathing
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.model.session.Session
import com.ead.dispatch.sample.domain.util.simpleDeepseekExecutor
import kotlin.io.path.Path

class ChatAgent(
    private val apiKey : String,
) {
    private val applicationDirectory = Pathing.applicationDirectory
    private val storageProvider = Storage.provider


    fun create(session: Session) = AIAgent(
        promptExecutor = simpleDeepseekExecutor(apiToken = apiKey),
        agentConfig = AIAgentConfig(
            prompt = prompt("assistant") {
                system(
                    "You're a helpful assistant.",
                )
            },
            model = DeepSeekModels.DeepSeekChat,
            maxAgentIterations = 50
        ),
        strategy = strategy<String, Message.Response>("assistant.strategy") {
            val llmCall by nodeLLMRequest()

            edge(nodeStart forwardTo llmCall)
            edge(llmCall forwardTo nodeFinish)
        },
        id = session.id, // Use session ID as agent ID for persistence
    ) {
        install(AgentMemory) {
            val memoryProvider = LocalFileMemoryProvider(
                config = LocalMemoryConfig(applicationDirectory.toAbsolutePath().toString()),
                storage = EncryptedStorage(
                    fs = JVMFileSystemProvider.ReadWrite,
                    encryption = Aes256GCMEncryptor("7UL8fsTqQDq9siUZgYO3bLGqwMGXQL4vKMWMscKB7Cw=")
                ),
                fs = JVMFileSystemProvider.ReadWrite,
                root = Path(applicationDirectory.toAbsolutePath().toString())
            )

            // Use in-memory storage for snapshots
            this.memoryProvider = memoryProvider
        }

        install(Persistence) {
            // Use in-memory storage for snapshots
            storage = storageProvider
            // Enable automatic persistence after each node
            enableAutomaticPersistence = true
            rollbackStrategy = RollbackStrategy.MessageHistoryOnly
        }
    }
}