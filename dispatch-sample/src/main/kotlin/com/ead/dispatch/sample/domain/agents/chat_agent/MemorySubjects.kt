package com.ead.dispatch.sample.domain.agents.chat_agent

import ai.koog.agents.memory.model.MemorySubject
import kotlinx.serialization.Serializable

object MemorySubjects {
    /**
     * Information specific to the user
     * Examples: Conversation preferences, issue history, contact information
     */
    @Serializable
    data object User : MemorySubject() {
        override val name: String = "user"
        override val promptDescription: String =
            "User information (language, response tone, formatting preferences, interaction style, etc.)"
        override val priorityLevel: Int = 1
    }

    // Chat-mode memory uses only user preferences (no story/org subjects).
}
