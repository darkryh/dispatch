@file:Suppress("unused")

package com.ead.dispatch.sample.domain.agents.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.tools.model.*
import java.util.UUID

class InteractionTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {

    @Tool
    @LLMDescription(
        """
        Ask the user to choose between options when clarification is required.
        Use only when user input is needed to continue safely.
        """
    )
    suspend fun requestUserChoice(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Choice question and options")
        request: UserChoiceRequest,
    ): ToolResult<QueryOutcome<UserChoicePayload>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")

        val question = request.question.trim()
        if (question.isBlank()) {
            return failure("MISSING_FIELD", "Question is required.")
        }

        val rawOptions = request.options
            .mapIndexedNotNull { index, option ->
                val label = option.label.trim()
                if (label.isBlank()) return@mapIndexedNotNull null
                val id = option.id?.trim().takeUnless { it.isNullOrBlank() }
                    ?: decisionOptionId(index)
                ChoiceOptionPayload(id = id, label = label)
            }

        if (rawOptions.size < MIN_OPTIONS || rawOptions.size > MAX_OPTIONS) {
            return failure(
                "INVALID_OPTIONS",
                "Choice options must include between $MIN_OPTIONS and $MAX_OPTIONS items.",
            )
        }

        val normalizedOptions = rawOptions
            .distinctBy { it.id.uppercase() to it.label.lowercase() }
            .take(MAX_OPTIONS)

        if (normalizedOptions.size < MIN_OPTIONS) {
            return failure(
                "INVALID_OPTIONS",
                "Choice options must include at least $MIN_OPTIONS unique items.",
            )
        }

        val minChoices = request.minChoices.coerceAtLeast(1)
        val maxChoices = request.maxChoices.coerceAtLeast(minChoices)
        val allowCustom = request.allowCustom
        val promptId = request.promptId?.trim().takeUnless { it.isNullOrBlank() }
            ?: UUID.randomUUID().toString()
        val payload = UserChoicePayload(
            promptId = promptId,
            question = question,
            options = normalizedOptions,
            allowCustom = allowCustom,
            minChoices = minChoices,
            maxChoices = maxChoices,
            customPlaceholder = request.customPlaceholder
                ?.trim()
                ?.takeUnless { it.isBlank() }
                ?: DEFAULT_DECISION_PLACEHOLDER,
        )

        return querySuccess(
            entity = OperationEntity.STORY,
            storyId = storyId,
            entityId = storyId,
            summary = "User choice required.",
            payload = payload,
        )
    }

    private fun decisionOptionId(index: Int): String {
        require(index >= 0)
        return if (index < 26) {
            ('A'.code + index).toChar().toString()
        } else {
            "OPT_${index + 1}"
        }
    }

    private companion object {
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 5
    }
}
