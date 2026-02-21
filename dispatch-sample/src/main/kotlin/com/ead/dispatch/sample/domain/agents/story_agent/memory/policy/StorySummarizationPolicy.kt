package com.ead.dispatch.sample.domain.agents.story_agent.memory.policy

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun interface StorySummarizationPolicy {
    suspend fun decide(input: StorySummarizationPolicyInput): StorySummarizationDecision
}

enum class StorySummarizationAction {
    SUMMARIZE_NOW,
    DEFER,
    SKIP,
}

data class StorySummarizationPolicyInput(
    val storyId: String,
    val chapterId: String,
    val chapterNumber: Long,
    val chapterTitle: String,
    val approvedChecksum: String,
    val approvedWordCount: Int,
    val hasExistingSummary: Boolean,
    val previousApprovedChecksum: String?,
    val millisSinceLastSummary: Long?,
    val existingOpenThreadCount: Int,
    val existingRiskCount: Int,
)

data class StorySummarizationDecision(
    val action: StorySummarizationAction,
    val reason: String,
    val confidence: String,
)

object SummarizeNowStorySummarizationPolicy : StorySummarizationPolicy {
    override suspend fun decide(input: StorySummarizationPolicyInput): StorySummarizationDecision =
        StorySummarizationDecision(
            action = StorySummarizationAction.SUMMARIZE_NOW,
            reason = "Forced summarize policy for deterministic execution.",
            confidence = "HIGH",
        )
}

object SkipStorySummarizationPolicy : StorySummarizationPolicy {
    override suspend fun decide(input: StorySummarizationPolicyInput): StorySummarizationDecision =
        StorySummarizationDecision(
            action = StorySummarizationAction.SKIP,
            reason = "Summarization policy unavailable.",
            confidence = "LOW",
        )
}

class KoogStorySummarizationPolicy : StorySummarizationPolicy {
    override suspend fun decide(input: StorySummarizationPolicyInput): StorySummarizationDecision {
        val agent = AIAgent<StorySummarizationPolicyInput, Result<StructuredResponse<StorySummarizationDecisionDraft>>, >(
            promptExecutor = AIProvider.Sync.executor,
            llmModel = AIProvider.Story.intent,
            strategy = strategy<StorySummarizationPolicyInput, Result<StructuredResponse<StorySummarizationDecisionDraft>>>("story-summarization-policy") {
                val decideNode by nodeDecideStorySummarizationPolicy()
                edge(nodeStart forwardTo decideNode)
                edge(decideNode forwardTo nodeFinish transformed { it })
            },
            responseProcessor = null,
            maxIterations = 3,
            temperature = 0.0,
            id = "story-summarization-policy",
        )

        val decision = runCatching { agent.run(input).getOrThrow().data }.getOrNull()
            ?: return StorySummarizationDecision(
                action = StorySummarizationAction.SKIP,
                reason = "Policy model failed.",
                confidence = "LOW",
            )

        return StorySummarizationDecision(
            action = decision.action.toAction(),
            reason = decision.reason.trim().ifBlank { "Policy decided without explicit reason." },
            confidence = decision.confidence.name,
        )
    }
}

@Serializable
private enum class SummarizationActionLabel {
    @SerialName("SUMMARIZE_NOW")
    SUMMARIZE_NOW,

    @SerialName("DEFER")
    DEFER,

    @SerialName("SKIP")
    SKIP,
}

@Serializable
private enum class ConfidenceBandLabel {
    @SerialName("HIGH")
    HIGH,

    @SerialName("MEDIUM")
    MEDIUM,

    @SerialName("LOW")
    LOW,
}

@Serializable
private data class StorySummarizationDecisionDraft(
    val action: SummarizationActionLabel,
    val reason: String = "",
    val confidence: ConfidenceBandLabel = ConfidenceBandLabel.MEDIUM,
)

@AIAgentBuilderDslMarker
private fun AIAgentSubgraphBuilderBase<*, *>.nodeDecideStorySummarizationPolicy(
    name: String? = null,
): AIAgentNodeDelegate<StorySummarizationPolicyInput, Result<StructuredResponse<StorySummarizationDecisionDraft>>> =
    node(name ?: "decide-story-summarization-policy") { input ->
        decideStorySummarizationPolicy(input)
    }

private suspend fun AIAgentContext.decideStorySummarizationPolicy(
    input: StorySummarizationPolicyInput,
): Result<StructuredResponse<StorySummarizationDecisionDraft>> = llm.writeSession {
    this.model = AIProvider.Story.intent
    rewritePrompt {
        storySummarizationPolicyPrompt(input)
    }
    requestLLMStructured<StorySummarizationDecisionDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.Story.fixer,
            retries = 2,
        )
    )
}

private fun storySummarizationPolicyPrompt(input: StorySummarizationPolicyInput): Prompt =
    prompt("story-summarization-policy") {
        system {
            markdown {
                h2("Role")
                +"Decide whether story continuity summarization should run for this approved chapter update."
                br()
                +"Return structured output only."
                br()
                h2("Decision Contract")
                +"Use action=SUMMARIZE_NOW only when summarization is necessary to maintain continuity quality."
                br()
                +"Use action=DEFER when summarization should happen later, not now."
                br()
                +"Use action=SKIP when current continuity memory can remain unchanged."
                br()
                +"Avoid keyword or lexical heuristics. Base decision on semantic continuity impact and state metadata."
                br()
            }
        }
        user {
            markdown {
                +"story_id: ${input.storyId}"
                br()
                +"chapter_id: ${input.chapterId}"
                br()
                +"chapter_number: ${input.chapterNumber}"
                br()
                +"chapter_title: ${input.chapterTitle}"
                br()
                +"approved_checksum: ${input.approvedChecksum}"
                br()
                +"approved_word_count: ${input.approvedWordCount}"
                br()
                +"has_existing_summary: ${input.hasExistingSummary}"
                br()
                +"previous_approved_checksum: ${input.previousApprovedChecksum ?: "none"}"
                br()
                +"millis_since_last_summary: ${input.millisSinceLastSummary ?: -1}"
                br()
                +"existing_open_thread_count: ${input.existingOpenThreadCount}"
                br()
                +"existing_risk_count: ${input.existingRiskCount}"
                br()
            }
        }
    }

private fun SummarizationActionLabel.toAction(): StorySummarizationAction =
    when (this) {
        SummarizationActionLabel.SUMMARIZE_NOW -> StorySummarizationAction.SUMMARIZE_NOW
        SummarizationActionLabel.DEFER -> StorySummarizationAction.DEFER
        SummarizationActionLabel.SKIP -> StorySummarizationAction.SKIP
    }
