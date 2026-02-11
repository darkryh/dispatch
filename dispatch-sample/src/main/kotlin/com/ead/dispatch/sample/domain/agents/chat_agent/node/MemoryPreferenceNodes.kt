package com.ead.dispatch.sample.domain.agents.chat_agent.node

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.featureOrThrow
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.MultipleFacts
import ai.koog.agents.memory.model.SingleFact
import ai.koog.prompt.streaming.StreamFrame
import com.ead.koog.context.orchestrator.api.ContextualResponse
import com.ead.dispatch.sample.domain.agents.chat_agent.MemorySubjects
import com.ead.dispatch.sample.domain.agents.chat_agent.PreferencesMemory
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatDecisionPath
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnInput
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.currentChatTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.currentLastSavedPreferenceHash
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.storeLastSavedPreferenceHash
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.storeLoadedUserPreferencesContext
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.updateChatTurnMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow

internal const val preferenceSaveConfidenceThreshold = 0.75

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeLoadUserPreferences(
    name: String? = null,
    scope: MemoryScopeType = MemoryScopeType.PRODUCT,
): AIAgentNodeDelegate<ChatTurnInput, ChatTurnInput> =
    node(name ?: "load-user-preferences") { turnInput ->
        loadUserPreferencesOnce(turnInput, scope)
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeSaveUserPreferences(
    name: String? = null,
    scope: MemoryScopeType = MemoryScopeType.PRODUCT,
): AIAgentNodeDelegate<ContextualResponse<Flow<StreamFrame>>, ContextualResponse<Flow<StreamFrame>>> =
    node(name ?: "save-user-preferences") { response ->
        saveUserPreferencesOnce(response, scope)
    }

@OptIn(InternalAgentsApi::class)
private suspend fun AIAgentGraphContextBase.loadUserPreferencesOnce(
    turnInput: ChatTurnInput,
    scope: MemoryScopeType,
): ChatTurnInput {
    if (!shouldLoadUserPreferences(turnInput)) {
        storeLoadedUserPreferencesContext(null)
        return turnInput
    }

    val memory = featureOrThrow(AgentMemory.Feature)
    val scopeValue = memory.scopesProfile.getScope(scope)
    if (scopeValue == null) {
        storeLoadedUserPreferencesContext(null)
        return turnInput
    }

    val sections = buildList {
        PreferencesMemory.userConcepts.forEach { concept ->
            val facts = memory.agentMemory.load(concept, MemorySubjects.User, scopeValue)
            val section = concept.toKoogFactSection(facts)
            if (!section.isNullOrBlank()) add(section)
        }
    }

    val promptContext = if (sections.isEmpty()) {
        null
    } else {
        sections.joinToString(separator = "\n\n")
    }
    storeLoadedUserPreferencesContext(promptContext)

    return turnInput
}

internal fun shouldLoadUserPreferences(turnInput: ChatTurnInput): Boolean {
    if (turnInput.policy.fromDecisionPrompt) return false

    return when (turnInput.policy.decisionPath) {
        ChatDecisionPath.DIRECT_RESPONSE,
        ChatDecisionPath.DIRECT_WRITE -> true
        ChatDecisionPath.FOLLOW_UP,
        ChatDecisionPath.SELECTOR -> false
    }
}

@OptIn(InternalAgentsApi::class)
private suspend fun AIAgentGraphContextBase.saveUserPreferencesOnce(
    response: ContextualResponse<Flow<StreamFrame>>,
    scope: MemoryScopeType,
): ContextualResponse<Flow<StreamFrame>> = kotlinx.coroutines.coroutineScope {
    val policy = currentChatTurnPolicy()
    if (policy == null) {
        return@coroutineScope response
    }

    val previousSavedHash = currentLastSavedPreferenceHash()
    val skipReason = preferenceSaveSkipReason(policy, previousSavedHash)
    if (skipReason != null) {
        updateChatTurnMetrics { metrics ->
            metrics.preferenceSaveRecommended = policy.shouldSavePreference
            metrics.preferenceConceptsSuggested = policy.preferenceConceptKeywords.joinToString(",")
            metrics.preferenceSaveExecuted = false
            metrics.preferenceSaveSkippedReason = skipReason
        }
        return@coroutineScope response
    }

    val conceptsToSave = resolvePreferenceConcepts(policy.preferenceConceptKeywords)
    if (conceptsToSave.isEmpty()) {
        updateChatTurnMetrics { metrics ->
            metrics.preferenceSaveRecommended = policy.shouldSavePreference
            metrics.preferenceConceptsSuggested = policy.preferenceConceptKeywords.joinToString(",")
            metrics.preferenceSaveExecuted = false
            metrics.preferenceSaveSkippedReason = "no_resolved_concepts"
        }
        return@coroutineScope response
    }

    val memory = featureOrThrow(AgentMemory.Feature)
    val scopeValue = requireNotNull(memory.scopesProfile.getScope(scope)) {
        "Memory scope name missing for $scope."
    }

    conceptsToSave
        .map { concept ->
            async(Dispatchers.IO) {
                memory.saveFactsFromHistory(
                    llm = llm,
                    concept = concept,
                    subject = MemorySubjects.User,
                    scope = scopeValue,
                )
            }
        }
        .awaitAll()

    storeLastSavedPreferenceHash(policy.requestTextHash)
    updateChatTurnMetrics { metrics ->
        metrics.preferenceSaveRecommended = policy.shouldSavePreference
        metrics.preferenceConceptsSuggested = policy.preferenceConceptKeywords.joinToString(",")
        metrics.preferenceSaveExecuted = true
        metrics.preferenceSaveSkippedReason = ""
    }

    response
}

internal fun preferenceSaveSkipReason(
    policy: ChatTurnPolicy,
    previousSavedHash: String?,
): String? {
    if (policy.fromDecisionPrompt) return "from_decision_prompt"
    if (!policy.shouldSavePreference) return "classifier_not_recommended"
    if (policy.preferenceConfidence < preferenceSaveConfidenceThreshold) return "low_confidence"
    if (policy.preferenceConceptKeywords.isEmpty()) return "no_target_concepts"
    if (policy.requestTextHash.isBlank()) return "missing_request_hash"
    if (previousSavedHash == policy.requestTextHash) return "duplicate_message_hash"
    return null
}

internal fun resolvePreferenceConcepts(keywords: List<String>): List<Concept> {
    if (keywords.isEmpty()) return emptyList()

    val conceptsByKeyword = PreferencesMemory.userConcepts.associateBy { it.keyword.lowercase() }
    return keywords
        .asSequence()
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
        .mapNotNull { keyword -> conceptsByKeyword[keyword] }
        .toList()
}

private fun Concept.toKoogFactSection(facts: List<Fact>): String? {
    if (facts.isEmpty()) return null

    val lines = buildList {
        add("Here are the relevant facts from memory about [$keyword](${description.shortenedForMemory()}):")

        facts.forEach { fact ->
            when (fact) {
                is SingleFact -> {
                    val value = fact.value.trim()
                    if (value.isNotEmpty()) {
                        add("- [${fact.concept.keyword}]: $value")
                    }
                }
                is MultipleFacts -> {
                    val values = fact.values
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (values.isNotEmpty()) {
                        add("- [${fact.concept.keyword}]:")
                        values.forEach { value ->
                            add("  - $value")
                        }
                    }
                }
            }
        }
    }

    if (lines.size == 1) return null
    return lines.joinToString(separator = "\n")
}

private fun String.shortenedForMemory(): String = lines().firstOrNull()
    ?.take(100)
    ?.plus("...")
    ?: "..."
