package com.ead.dispatch.sample.domain.agents.chat_agent.node

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.featureOrThrow
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.MemorySubjects
import com.ead.dispatch.sample.domain.agents.chat_agent.PreferencesMemory
import kotlinx.coroutines.flow.Flow

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeLoadUserPreferences(
    name: String? = null,
    scope: MemoryScopeType = MemoryScopeType.PRODUCT,
): AIAgentNodeDelegate<ChatRequest, ChatRequest> =
    node(name ?: "load-user-preferences") { request ->
        loadUserPreferencesOnce(request, scope)
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeSaveUserPreferences(
    name: String? = null,
    scope: MemoryScopeType = MemoryScopeType.PRODUCT,
): AIAgentNodeDelegate<Flow<StreamFrame>, Flow<StreamFrame>> =
    node(name ?: "save-user-preferences") { response ->
        saveUserPreferencesOnce(response, scope)
    }

@OptIn(InternalAgentsApi::class)
private suspend fun AIAgentGraphContextBase.loadUserPreferencesOnce(
    request: ChatRequest,
    scope: MemoryScopeType,
): ChatRequest {
    val memory = featureOrThrow(AgentMemory.Feature)
    PreferencesMemory.userConcepts.forEach { concept ->
        memory.loadFactsToAgent(
            llm = llm,
            concept = concept,
            scopes = listOf(scope),
            subjects = listOf(MemorySubjects.User),
        )
    }
    return request
}

@OptIn(InternalAgentsApi::class)
private suspend fun AIAgentGraphContextBase.saveUserPreferencesOnce(
    response: Flow<StreamFrame>,
    scope: MemoryScopeType,
): Flow<StreamFrame> {
    val memory = featureOrThrow(AgentMemory.Feature)
    val scopeValue = requireNotNull(memory.scopesProfile.getScope(scope)) {
        "Memory scope name missing for $scope."
    }
    PreferencesMemory.userConcepts.forEach { concept ->
        memory.saveFactsFromHistory(
            llm = llm,
            concept = concept,
            subject = MemorySubjects.User,
            scope = scopeValue,
        )
    }
    return response
}
