package com.ead.dispatch.sample.domain.agents.story_agent.policy

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest

private val storyTurnPolicyKey: AIAgentStorageKey<StoryTurnPolicy> =
    AIAgentStorageKey("dispatch.story.turn.policy")

private val storyTurnMetricsKey: AIAgentStorageKey<StoryTurnMetrics> =
    AIAgentStorageKey("dispatch.story.turn.metrics")

private val loadedStoryPreferencesContextKey: AIAgentStorageKey<String> =
    AIAgentStorageKey("dispatch.story.turn.loaded_story_preferences_context")

private val lastSavedPreferenceHashKey: AIAgentStorageKey<String> =
    AIAgentStorageKey("dispatch.story.turn.last_saved_preference_hash")

private val storyTurnRequestKey: AIAgentStorageKey<StoryRequest> =
    AIAgentStorageKey("dispatch.story.turn.request")

suspend fun AIAgentGraphContextBase.storeStoryTurnPolicy(policy: StoryTurnPolicy) {
    store(storyTurnPolicyKey, policy)
    store(storyTurnMetricsKey, policy.toMetrics())
}

suspend fun AIAgentGraphContextBase.currentStoryTurnPolicy(): StoryTurnPolicy? =
    get(storyTurnPolicyKey) as? StoryTurnPolicy

suspend fun AIAgentGraphContextBase.currentStoryTurnMetrics(): StoryTurnMetrics? =
    get(storyTurnMetricsKey) as? StoryTurnMetrics

suspend fun AIAgentGraphContextBase.updateStoryTurnMetrics(update: (StoryTurnMetrics) -> Unit) {
    val metrics = currentStoryTurnMetrics() ?: return
    update(metrics)
    store(storyTurnMetricsKey, metrics)
}

suspend fun AIAgentGraphContextBase.storeLoadedStoryPreferencesContext(context: String?) {
    if (context.isNullOrBlank()) {
        remove(loadedStoryPreferencesContextKey)
        return
    }
    store(loadedStoryPreferencesContextKey, context)
}

suspend fun AIAgentGraphContextBase.currentLoadedStoryPreferencesContext(): String? =
    get(loadedStoryPreferencesContextKey) as? String

suspend fun AIAgentGraphContextBase.storeStoryLastSavedPreferenceHash(hash: String?) {
    if (hash.isNullOrBlank()) {
        remove(lastSavedPreferenceHashKey)
        return
    }
    store(lastSavedPreferenceHashKey, hash)
}

suspend fun AIAgentGraphContextBase.currentStoryLastSavedPreferenceHash(): String? =
    get(lastSavedPreferenceHashKey) as? String

suspend fun AIAgentGraphContextBase.storeStoryTurnRequest(request: StoryRequest) {
    store(storyTurnRequestKey, request)
}

suspend fun AIAgentGraphContextBase.currentStoryTurnRequest(): StoryRequest? =
    get(storyTurnRequestKey) as? StoryRequest
