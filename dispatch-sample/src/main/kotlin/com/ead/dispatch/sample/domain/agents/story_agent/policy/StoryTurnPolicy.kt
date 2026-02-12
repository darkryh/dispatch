package com.ead.dispatch.sample.domain.agents.story_agent.policy

import kotlinx.serialization.Serializable

@Serializable
enum class StoryIntentClass {
    CREATIVE,
    WRITE,
    AMBIGUOUS,
    DESTRUCTIVE,
}

@Serializable
enum class StoryDecisionPath {
    DIRECT_RESPONSE,
    DIRECT_WRITE,
    SELECTOR,
    FOLLOW_UP,
}

@Serializable
data class StoryIntentSignal(
    val intentClass: StoryIntentClass = StoryIntentClass.AMBIGUOUS,
    val explicitWriteIntent: Boolean = false,
    val confidence: Double = 0.0,
    val evidenceSpan: String = "",
    val reasoning: String = "",
)

@Serializable
data class StoryTurnPolicy(
    val intentClass: StoryIntentClass,
    val decisionPath: StoryDecisionPath,
    val explicitWriteIntent: Boolean,
    val allowWriteTools: Boolean,
    val requireSelectorForDestructive: Boolean,
    val rationale: String,
    val fromDecisionPrompt: Boolean,
    val requestTextHash: String = "",
)

@Serializable
data class StoryTurnMetrics(
    val intentClass: StoryIntentClass,
    val decisionPath: StoryDecisionPath,
    var requestedToolCalls: Int = 0,
    var executedToolCalls: Int = 0,
    var blockedToolCalls: Int = 0,
    var failedToolCalls: Int = 0,
    var writeToolCalls: Int = 0,
    var writeToolCallsBlocked: Int = 0,
    var decisionToolCalls: Int = 0,
    var mutationCreateCount: Int = 0,
    var mutationUpdateCount: Int = 0,
    var mutationDeleteCount: Int = 0,
    var selectorShown: Boolean = false,
    var auditNodeVisited: Boolean = false,
)

fun StoryTurnPolicy.toMetrics(): StoryTurnMetrics = StoryTurnMetrics(
    intentClass = intentClass,
    decisionPath = decisionPath,
)
