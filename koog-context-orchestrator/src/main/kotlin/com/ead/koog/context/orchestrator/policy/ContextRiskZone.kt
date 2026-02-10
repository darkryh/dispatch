package com.ead.koog.context.orchestrator.policy

enum class ContextRiskZone {
    HEALTHY,
    WATCH,
    WARNING,
    CRITICAL,
    EMERGENCY,
}

enum class CompressionMode {
    NONE,
    LIGHT,
    STRUCTURED,
    AGGRESSIVE,
    FACT_FOCUSED,
    EMERGENCY,
}
