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
import com.ead.koog.context.orchestrator.api.ContextRunOutput
import com.ead.dispatch.sample.domain.agents.chat_agent.MemorySubjects
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.SelectorPreferencesMemory
import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.currentChatTurnRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatPreferenceNovelty
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnInput
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.currentChatTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.currentLastSavedPreferenceHash
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.storeLastSavedPreferenceHash
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.storeLoadedChatPreferencesContext
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.updateChatTurnMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow

/**
 * Loads reusable chat readability preference into prompt context.
 * This node does not persist new preferences; it only prepares retrieval context.
 */
@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeLoadChatPreferences(
    name: String? = null,
    scope: MemoryScopeType = MemoryScopeType.PRODUCT,
): AIAgentNodeDelegate<ChatTurnInput, ChatTurnInput> =
    node(name ?: "load-chat-preferences") { turnInput ->
        loadChatPreferencesOnce(turnInput, scope)
    }

/**
 * Persists chat preferences for selector-driven turns only.
 * Save is deduplicated by turn request hash.
 */
@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeSaveChatPreferences(
    name: String? = null,
    scope: MemoryScopeType = MemoryScopeType.PRODUCT,
): AIAgentNodeDelegate<ContextRunOutput<Flow<StreamFrame>>, ContextRunOutput<Flow<StreamFrame>>> =
    node(name ?: "save-chat-preferences") { response ->
        saveChatPreferencesOnce(response, scope)
    }

@OptIn(InternalAgentsApi::class)
private suspend fun AIAgentGraphContextBase.loadChatPreferencesOnce(
    turnInput: ChatTurnInput,
    scope: MemoryScopeType,
): ChatTurnInput {
    val memory = featureOrThrow(AgentMemory.Feature)
    val scopeValue = memory.scopesProfile.getScope(scope)
    if (scopeValue == null) {
        storeLoadedChatPreferencesContext(null)
        return turnInput
    }

    val sections = buildList {
        SelectorPreferencesMemory.userConcepts.forEach { concept ->
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
    storeLoadedChatPreferencesContext(promptContext)

    return turnInput
}

internal fun shouldLoadChatPreferences(@Suppress("UNUSED_PARAMETER") turnInput: ChatTurnInput): Boolean = true

@OptIn(InternalAgentsApi::class)
private suspend fun AIAgentGraphContextBase.saveChatPreferencesOnce(
    response: ContextRunOutput<Flow<StreamFrame>>,
    scope: MemoryScopeType,
): ContextRunOutput<Flow<StreamFrame>> = kotlinx.coroutines.coroutineScope {
    val policy = currentChatTurnPolicy()
    val request = currentChatTurnRequest()
    if (policy == null) {
        return@coroutineScope response
    }

    val previousSavedHash = currentLastSavedPreferenceHash()
    val skipReason = preferenceSaveSkipReason(policy, request, previousSavedHash)
    if (skipReason != null) {
        updateChatTurnMetrics { metrics ->
            metrics.preferenceSaveRecommended = policy.shouldSavePreference
            metrics.preferenceConceptsSuggested = policy.preferenceConceptKeywords.joinToString(",")
            metrics.preferenceConfidenceBand = policy.preferenceConfidenceBand.name
            metrics.preferenceNovelty = policy.preferenceNovelty.name
            metrics.preferenceSaveExecuted = false
            metrics.preferenceSaveSkippedReason = skipReason
        }
        return@coroutineScope response
    }

    val conceptsToSave = resolveChatPreferenceConcepts(policy.preferenceConceptKeywords)
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
        metrics.preferenceConceptsSuggested = conceptsToSave.joinToString(",") { it.keyword }
        metrics.preferenceConfidenceBand = policy.preferenceConfidenceBand.name
        metrics.preferenceNovelty = policy.preferenceNovelty.name
        metrics.preferenceSaveExecuted = true
        metrics.preferenceSaveSkippedReason = ""
    }

    response
}

internal fun preferenceSaveSkipReason(
    policy: ChatTurnPolicy,
    request: ChatRequest?,
    previousSavedHash: String?,
): String? {
    // Chat preference save is valid only after a selector decision is submitted.
    if (!policy.fromDecisionPrompt) return "chat_non_selector_preference_save_disabled"
    if (!policy.shouldSavePreference) return "classifier_not_recommended"
    if (policy.preferenceConfidenceBand == IntentConfidenceBand.LOW) return "low_confidence"
    if (policy.preferenceNovelty == ChatPreferenceNovelty.ALREADY_KNOWN) return "already_known"
    if (
        policy.preferenceNovelty == ChatPreferenceNovelty.UNCERTAIN &&
        policy.preferenceConfidenceBand != IntentConfidenceBand.HIGH
    ) {
        return "uncertain_low_confidence"
    }
    if (policy.preferenceConceptKeywords.isEmpty()) return "no_target_concepts"
    if (request?.decisionContext == null) return "missing_decision_context"
    if (policy.requestTextHash.isBlank()) return "missing_request_hash"
    if (previousSavedHash == policy.requestTextHash) return "duplicate_message_hash"
    return null
}

internal fun resolveChatPreferenceConcepts(keywords: List<String>): List<Concept> {
    if (keywords.isEmpty()) return emptyList()
    val conceptsByKeyword = SelectorPreferencesMemory.userConcepts.associateBy { it.keyword.lowercase() }
    return keywords
        .asSequence()
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
        .mapNotNull { conceptsByKeyword[it] }
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
