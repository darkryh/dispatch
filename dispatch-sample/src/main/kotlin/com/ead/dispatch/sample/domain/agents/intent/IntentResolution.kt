package com.ead.dispatch.sample.domain.agents.intent

import kotlinx.serialization.Serializable

@Serializable
enum class IntentResolvedAction {
    ADVISE,
    WRITE_CREATE,
    WRITE_UPDATE,
    WRITE_DELETE,
    FOLLOW_UP,
}

@Serializable
enum class IntentConfidenceBand {
    HIGH,
    MEDIUM,
    LOW,
}

@Serializable
enum class IntentRiskClass {
    SAFE,
    DESTRUCTIVE,
}

@Serializable
enum class IntentExecutionIntent {
    EXECUTE,
    INQUIRE,
}

@Serializable
data class IntentResolution(
    val action: IntentResolvedAction = IntentResolvedAction.FOLLOW_UP,
    val confidenceBand: IntentConfidenceBand = IntentConfidenceBand.LOW,
    val riskClass: IntentRiskClass = IntentRiskClass.SAFE,
    val anchorHint: String = "",
    val requiresConfirmation: Boolean = false,
    val reasoning: String = "",
)
