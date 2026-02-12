package com.ead.dispatch.sample.domain.agents.story_agent.policy

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey

private val storyTurnPolicyKey: AIAgentStorageKey<StoryTurnPolicy> =
    AIAgentStorageKey("dispatch.story.turn.policy")

private val storyTurnMetricsKey: AIAgentStorageKey<StoryTurnMetrics> =
    AIAgentStorageKey("dispatch.story.turn.metrics")

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
