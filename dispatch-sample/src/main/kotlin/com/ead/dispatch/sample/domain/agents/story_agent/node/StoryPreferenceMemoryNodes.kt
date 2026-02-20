package com.ead.dispatch.sample.domain.agents.story_agent.node

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
import com.ead.dispatch.sample.domain.agents.chat_agent.MemorySubjects
import com.ead.dispatch.sample.domain.agents.chat_agent.PreferencesMemory
import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.story_agent.policy.PreferenceNovelty
import com.ead.dispatch.sample.domain.agents.story_agent.policy.currentStoryLastSavedPreferenceHash
import com.ead.dispatch.sample.domain.agents.story_agent.policy.currentStoryTurnPolicy
import com.ead.dispatch.sample.domain.agents.story_agent.policy.currentStoryTurnRequest
import com.ead.dispatch.sample.domain.agents.story_agent.policy.storeLoadedStoryPreferencesContext
import com.ead.dispatch.sample.domain.agents.story_agent.policy.storeStoryLastSavedPreferenceHash
import com.ead.dispatch.sample.domain.agents.story_agent.policy.updateStoryTurnMetrics
import com.ead.koog.context.orchestrator.api.ContextualResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow

/**
 * Loads reusable story preferences into prompt context before the story turn is executed.
 */
@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeLoadStoryPreferences(
    name: String? = null,
    scope: MemoryScopeType = MemoryScopeType.PRODUCT,
): AIAgentNodeDelegate<StoryTurnInput, StoryTurnInput> =
    node(name ?: "story-load-story-preferences") { turnInput ->
        loadStoryPreferencesOnce(turnInput, scope)
    }

/**
 * Persists writer preferences from story-mode history.
 * Save is deduplicated by turn request hash.
 */
@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeSaveStoryPreferences(
    name: String? = null,
    scope: MemoryScopeType = MemoryScopeType.PRODUCT,
): AIAgentNodeDelegate<ContextualResponse<Flow<StreamFrame>>, ContextualResponse<Flow<StreamFrame>>> =
    node(name ?: "story-save-story-preferences") { response ->
        saveStoryPreferencesOnce(response, scope)
    }

@OptIn(InternalAgentsApi::class)
private suspend fun AIAgentGraphContextBase.loadStoryPreferencesOnce(
    turnInput: StoryTurnInput,
    scope: MemoryScopeType,
): StoryTurnInput {
    if (turnInput.policy.fromDecisionPrompt) {
        storeLoadedStoryPreferencesContext(null)
        return turnInput
    }

    val memory = featureOrThrow(AgentMemory.Feature)
    val scopeValue = memory.scopesProfile.getScope(scope)
    if (scopeValue == null) {
        storeLoadedStoryPreferencesContext(null)
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
    storeLoadedStoryPreferencesContext(promptContext)

    return turnInput
}

@OptIn(InternalAgentsApi::class)
private suspend fun AIAgentGraphContextBase.saveStoryPreferencesOnce(
    response: ContextualResponse<Flow<StreamFrame>>,
    scope: MemoryScopeType,
): ContextualResponse<Flow<StreamFrame>> = kotlinx.coroutines.coroutineScope {
    val policy = currentStoryTurnPolicy() ?: return@coroutineScope response
    val request = currentStoryTurnRequest() ?: return@coroutineScope response
    val previousSavedHash = currentStoryLastSavedPreferenceHash()
    val conceptsToSave = resolveStoryPreferenceConcepts(policy.preferenceConceptKeywords)

    val skipReason = storyPreferenceSaveSkipReason(
        fromDecisionPrompt = policy.fromDecisionPrompt,
        shouldSavePreference = policy.shouldSavePreference,
        preferenceConfidenceBand = policy.preferenceConfidenceBand,
        preferenceNovelty = policy.preferenceNovelty,
        hasResolvedConcepts = conceptsToSave.isNotEmpty(),
        requestTextHash = policy.requestTextHash,
        previousSavedHash = previousSavedHash,
    )
    if (skipReason != null) {
        updateStoryTurnMetrics { metrics ->
            metrics.preferenceSaveRecommended = policy.shouldSavePreference
            metrics.preferenceConceptsSuggested = policy.preferenceConceptKeywords.joinToString(",")
            metrics.preferenceConfidenceBand = policy.preferenceConfidenceBand.name
            metrics.preferenceNovelty = policy.preferenceNovelty.name
            metrics.preferenceSaveExecuted = false
            metrics.preferenceSaveSkippedReason = skipReason
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

    storeStoryLastSavedPreferenceHash(policy.requestTextHash)
    updateStoryTurnMetrics { metrics ->
        metrics.preferenceSaveRecommended = policy.shouldSavePreference
        metrics.preferenceConceptsSuggested = conceptsToSave.joinToString(",") { it.keyword }
        metrics.preferenceConfidenceBand = policy.preferenceConfidenceBand.name
        metrics.preferenceNovelty = policy.preferenceNovelty.name
        metrics.preferenceSaveExecuted = true
        metrics.preferenceSaveSkippedReason = ""
        metrics.preferenceSaveRequestHash = request.text.take(120)
    }

    response
}

internal fun storyPreferenceSaveSkipReason(
    fromDecisionPrompt: Boolean,
    shouldSavePreference: Boolean,
    preferenceConfidenceBand: IntentConfidenceBand,
    preferenceNovelty: PreferenceNovelty,
    hasResolvedConcepts: Boolean,
    requestTextHash: String,
    previousSavedHash: String?,
): String? {
    if (fromDecisionPrompt) return "from_decision_prompt"
    if (!shouldSavePreference) return "classifier_not_recommended"
    if (preferenceConfidenceBand == IntentConfidenceBand.LOW) return "low_confidence"
    if (preferenceNovelty == PreferenceNovelty.ALREADY_KNOWN) return "already_known"
    if (
        preferenceNovelty == PreferenceNovelty.UNCERTAIN &&
        preferenceConfidenceBand != IntentConfidenceBand.HIGH
    ) {
        return "uncertain_low_confidence"
    }
    if (!hasResolvedConcepts) return "no_target_concepts"
    if (requestTextHash.isBlank()) return "missing_request_hash"
    if (previousSavedHash == requestTextHash) return "duplicate_message_hash"
    return null
}

internal fun resolveStoryPreferenceConcepts(keywords: List<String>): List<Concept> {
    if (keywords.isEmpty()) return emptyList()
    val conceptsByKeyword = PreferencesMemory.userConcepts.associateBy { it.keyword.lowercase() }
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
