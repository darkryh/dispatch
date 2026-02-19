package com.ead.dispatch.sample.domain.agents.story_agent.policy

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import com.ead.dispatch.sample.domain.agents.intent.IntentResolvedAction
import com.ead.dispatch.sample.domain.agents.intent.IntentRiskClass
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private enum class StoryIntentClassifierLabel {
    @SerialName("CREATIVE")
    CREATIVE,

    @SerialName("WRITE")
    WRITE,

    @SerialName("AMBIGUOUS")
    AMBIGUOUS,

    @SerialName("DESTRUCTIVE")
    DESTRUCTIVE,
}

@Serializable
private enum class ResolvedActionLabel {
    @SerialName("ADVISE")
    ADVISE,

    @SerialName("WRITE_CREATE")
    WRITE_CREATE,

    @SerialName("WRITE_UPDATE")
    WRITE_UPDATE,

    @SerialName("WRITE_DELETE")
    WRITE_DELETE,

    @SerialName("FOLLOW_UP")
    FOLLOW_UP,
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
private enum class RiskClassLabel {
    @SerialName("SAFE")
    SAFE,

    @SerialName("DESTRUCTIVE")
    DESTRUCTIVE,
}

@Serializable
private enum class ExecutionIntentLabel {
    @SerialName("EXECUTE")
    EXECUTE,

    @SerialName("INQUIRE")
    INQUIRE,
}

@Serializable
private data class StoryIntentClassifierResponse(
    @SerialName("intent_class")
    val intentClass: StoryIntentClassifierLabel,
    @SerialName("explicit_write_intent")
    val explicitWriteIntent: Boolean = false,
    @SerialName("confidence")
    val confidence: Double = 0.0,
    @SerialName("evidence_span")
    val evidenceSpan: String = "",
    @SerialName("reasoning")
    val reasoning: String = "",
    @SerialName("resolved_action")
    val resolvedAction: ResolvedActionLabel = ResolvedActionLabel.FOLLOW_UP,
    @SerialName("confidence_band")
    val confidenceBand: ConfidenceBandLabel = ConfidenceBandLabel.LOW,
    @SerialName("risk_class")
    val riskClass: RiskClassLabel = RiskClassLabel.SAFE,
    @SerialName("anchor_hint")
    val anchorHint: String = "",
    @SerialName("requires_confirmation")
    val requiresConfirmation: Boolean = false,
    @SerialName("requires_creative_choice")
    val requiresCreativeChoice: Boolean = false,
    @SerialName("execution_intent")
    val executionIntent: ExecutionIntentLabel = ExecutionIntentLabel.INQUIRE,
)

suspend fun AIAgentContext.classifyStoryTurnIntentWithAI(request: StoryRequest): StoryIntentSignal {
    if (request.fromDecisionPrompt) {
        return StoryIntentSignal(
            intentClass = StoryIntentClass.WRITE,
            explicitWriteIntent = true,
            confidence = 1.0,
            evidenceSpan = request.text.take(140),
            reasoning = "User answered a story decision prompt; continue as write flow.",
            resolvedAction = IntentResolvedAction.WRITE_UPDATE,
            confidenceBand = IntentConfidenceBand.HIGH,
            riskClass = IntentRiskClass.SAFE,
            requiresConfirmation = false,
            requiresCreativeChoice = false,
            executionIntent = IntentExecutionIntent.EXECUTE,
        )
    }

    val userText = request.text.trim()
    if (userText.isBlank()) {
        return StoryIntentSignal(
            intentClass = StoryIntentClass.AMBIGUOUS,
            explicitWriteIntent = false,
            confidence = 0.0,
            evidenceSpan = "",
            reasoning = "Story message is blank.",
            resolvedAction = IntentResolvedAction.FOLLOW_UP,
            confidenceBand = IntentConfidenceBand.LOW,
            riskClass = IntentRiskClass.SAFE,
            requiresConfirmation = false,
            requiresCreativeChoice = false,
            executionIntent = IntentExecutionIntent.INQUIRE,
        )
    }

    val recentContext = llm.readSession {
        prompt.messages
            .filterNot { it is Message.System }
            .takeLast(8)
            .joinToString("\n") { message ->
                when (message) {
                    is Message.User -> "USER: ${message.content}".take(220)
                    is Message.Assistant -> "ASSISTANT: ${message.content}".take(220)
                    is Message.Tool.Call -> "TOOL_CALL: ${message.tool} ${message.content}".take(220)
                    is Message.Tool.Result -> "TOOL_RESULT: ${message.tool} ${message.content}".take(220)
                    else -> ""
                }
            }
    }

    val classified = runCatching<StoryIntentClassifierResponse> {
        llm.writeSession {
            val originalPrompt = prompt
            val originalModel = model
            this.model = AIProvider.Story.intent

            try {
                rewritePrompt {
                    storyTurnIntentClassifierPrompt(userText, recentContext)
                }

                requestLLMStructured<StoryIntentClassifierResponse>(
                    fixingParser = StructureFixingParser(
                        model = AIProvider.Story.fixer,
                        retries = 2,
                    )
                ).getOrThrow().data
            } finally {
                rewritePrompt { originalPrompt }
                model = originalModel
            }
        }
    }.getOrNull()

    if (classified == null) {
        return StoryIntentSignal(
            intentClass = StoryIntentClass.AMBIGUOUS,
            explicitWriteIntent = false,
            confidence = 0.0,
            evidenceSpan = userText.take(140),
            reasoning = "Story classifier failed; fallback to ambiguous.",
            resolvedAction = IntentResolvedAction.FOLLOW_UP,
            confidenceBand = IntentConfidenceBand.LOW,
            riskClass = IntentRiskClass.SAFE,
            requiresConfirmation = false,
            requiresCreativeChoice = false,
            executionIntent = IntentExecutionIntent.INQUIRE,
        )
    }

    val intentClass = when (classified.intentClass) {
        StoryIntentClassifierLabel.CREATIVE -> StoryIntentClass.CREATIVE
        StoryIntentClassifierLabel.WRITE -> StoryIntentClass.WRITE
        StoryIntentClassifierLabel.AMBIGUOUS -> StoryIntentClass.AMBIGUOUS
        StoryIntentClassifierLabel.DESTRUCTIVE -> StoryIntentClass.DESTRUCTIVE
    }

    return StoryIntentSignal(
        intentClass = intentClass,
        explicitWriteIntent = classified.explicitWriteIntent,
        confidence = classified.confidence.coerceIn(0.0, 1.0),
        evidenceSpan = classified.evidenceSpan.trim(),
        reasoning = classified.reasoning.trim(),
        resolvedAction = classified.resolvedAction.toResolvedAction(),
        confidenceBand = classified.confidenceBand.toConfidenceBand(),
        riskClass = classified.riskClass.toRiskClass(),
        anchorHint = classified.anchorHint.trim(),
        requiresConfirmation = classified.requiresConfirmation,
        requiresCreativeChoice = classified.requiresCreativeChoice,
        executionIntent = classified.executionIntent.toExecutionIntent(),
    )
}

private fun storyTurnIntentClassifierPrompt(userText: String, recentContext: String): Prompt = prompt("story-turn-intent-classifier") {
    system {
        markdown {
            h2("Role")
            +"Classify the latest STORY mode turn with language-agnostic, context-aware intent reasoning."
            br()
            +"Use message and recent context to resolve continuation intent."
            br()
            +"Interpret intent semantically (not by keywords), resolve continuation from context, and set requires_confirmation=true for destructive/high-risk actions."
            br()
            +"Speech-act precedence: classify by communicative act first, then action semantics."
            br()
            +"If the latest turn is primarily a question, capability check, permission check, hypothetical, comparison, or option-seeking request, set execution_intent=INQUIRE."
            br()
            +"Only set execution_intent=EXECUTE when the user is committing to apply changes now in this turn."
            br()
            +"Set requires_creative_choice=true when execution intent is clear but narrative direction selection should remain with the user before persisting changes."
            br()
            +"Do not set requires_creative_choice for simple bounded creates."
            br()
            +"Creative selector decision framework (semantic, not lexical):"
            br()
            numbered {
                item("Direction openness: multiple narrative branches are valid and not uniquely implied by constraints.")
                item("Constraint density: role/theme/tone/continuity-fit requirements remain partial, conflicting, or underspecified.")
                item("Continuity impact: the choice can significantly influence subsequent chapters/scenes/arcs.")
                item("Reversibility risk: locking one branch now is likely to cause costly continuity rework.")
                item("Delegated choice intent: user is explicitly or implicitly asking assistant to choose among narrative directions.")
            }
            br()
            +"Trigger requires_creative_choice=true when this framework indicates meaningful branch choice risk and user-direction preference should be preserved."
            br()
            +"If uncertain between direct creative write and creative selector on EXECUTE turns, prefer requires_creative_choice=true."
            br()
            +"Compound-turn precedence:"
            br()
            +"When a single turn both requests create/apply now and also expresses narrative-fit uncertainty (style/role/tone/continuity compatibility not decided), keep execution_intent=EXECUTE and set requires_creative_choice=true."
            br()
            +"In this compound case, selector comes before persistence; do not collapse into direct write."
            br()
            +"Examples are illustrative, not exhaustive."
            br()
            +"Do not infer creative selector from specific words alone."
            br()
            +"Use recent context to determine whether direction is already stabilized."
            br()
            +"Generic request families that may require creative selector:"
            br()
            bulleted {
                item("High-leverage story additions with open fit (protagonists, primary conflicts, world-rule pivots, chapter direction branches).")
                item("Multi-option story generation where one persisted branch must be selected.")
                item("Style/arc alignment asks where canon plausibly supports divergent directions.")
            }
            br()
            +"Do not trigger creative selector when:"
            br()
            bulleted {
                item("The story write is bounded and specific with clear constraints.")
                item("The direction/path has already been selected in prior turns.")
                item("The turn is inquiry-only (execution_intent=INQUIRE).")
                item("The turn is exploratory ideation with no persistence requested.")
            }
            br()
            +"Classify execution intent by speech act."
            br()
            +"Set execution_intent=INQUIRE for capability checks, hypotheticals, comparisons, option-seeking, and mixed ask-first phrasing."
            br()
            +"Set execution_intent=EXECUTE only when user clearly requests to apply/save/create/update/delete now."
            br()
            +"Decision continuation messages after a selector prompt should be EXECUTE."
            br()
            +"If uncertain between INQUIRE and EXECUTE, default to INQUIRE."
            br()
            +"Context-first intent detection rules:"
            br()
            numbered {
                item("Infer intent from the current turn plus recent workflow state, not lexical triggers.")
                item("If the turn continues an already prepared mutation target, classify as EXECUTE.")
                item("If the user refers to an existing chapter/scene/draft contextually, prefer WRITE_UPDATE unless delete/overwrite is clear.")
                item("If the action target is unclear, keep execution_intent=INQUIRE and resolved_action=FOLLOW_UP.")
                item("Do not require exact wording to infer continuation intent.")
            }
            br()
            +"Question-type examples (all should be INQUIRE unless user explicitly asks to apply now):"
            br()
            bulleted {
                item("Ability/capability: \"can you create a chapter like this?\", \"are you able to update this scene?\" -> INQUIRE")
                item("Hypothetical: \"what if we rewrite this chapter in first person?\" -> INQUIRE")
                item("Comparison/evaluation: \"is this draft better than before?\" -> INQUIRE")
                item("Option-seeking: \"which chapter direction should we choose?\" -> INQUIRE")
                item("Mixed ask-first: \"can you create it, or just explain first?\" -> INQUIRE")
                item("Shorthand confirmation questions: \"ready?\", \"looks good?\" -> INQUIRE")
                item("Non-English ability forms with same meaning are also INQUIRE (e.g., ES/PT/FR).")
            }
            br()
            +"Additional boundary examples:"
            br()
            bulleted {
                item("Permission + implied action: \"can you update this chapter now?\" -> INQUIRE")
                item("Destructive question: \"should we delete this scene?\" -> INQUIRE")
                item("Ambiguous shorthand: \"and this one?\", \"same here?\", \"do it?\" -> INQUIRE or FOLLOW_UP if target unclear")
                item("Mixed conflict: \"can you create it? don't apply yet\" -> INQUIRE")
                item("Phased request: \"explain first, then apply\" -> INQUIRE for this turn")
                item("Non-English shorthand inquiry with same meaning (ES/PT/FR) -> INQUIRE")
            }
            br()
            +"Execute examples:"
            br()
            bulleted {
                item("\"create chapter 3 now\", \"apply this draft\", \"delete this scene now\" -> EXECUTE")
                item("Short continuation acknowledgements after a prepared write plan/decision -> EXECUTE")
                item("\"proceed\", \"yes apply\" after selector/decision prompt -> EXECUTE")
                item("Destructive explicit command: \"delete this scene now\" -> EXECUTE with destructive risk/confirmation")
                item("Continuation commit after prepared target: \"let's go ahead\", \"apply it\", \"continue\" -> EXECUTE")
            }
            br()
            +"Creative selector examples:"
            br()
            bulleted {
                item("Execute + under-constrained fit: \"create a new protagonist now; choose the best fit for current cast and tone\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + branching narrative: \"add possible chapter directions and choose one that matches current arc tone\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + compatibility uncertainty: \"create a high-impact character now; uncertain what narrative style best integrates with existing chapters\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + delegated branch-fit: \"add a major ally and decide the best arc role for current continuity\" -> EXECUTE + requires_creative_choice=true")
                item("Bounded create: \"create three random side characters\" -> EXECUTE + requires_creative_choice=false")
                item("Precise story write: \"create chapter 4 opening in first-person past tense, 700 words\" -> EXECUTE + requires_creative_choice=false")
            }
            br()
            +"Additional contrastive examples:"
            br()
            bulleted {
                item("Early story bootstrap: \"create one random character for this new story\" -> EXECUTE + requires_creative_choice=false")
                item("Established continuity + open fit: \"add a protagonist and decide how they should connect to existing chapters\" -> EXECUTE + requires_creative_choice=true")
                item("Bounded story create: \"create a supporting medic, calm tone, chapter-2 ally role\" -> EXECUTE + requires_creative_choice=false")
                item("Compound mixed signal: \"create it now, but I'm unsure which narrative direction fits best\" -> EXECUTE + requires_creative_choice=true")
                item("Cross-entity branch coupling: \"add a character and aligned chapter direction that best fits current world rules\" -> EXECUTE + requires_creative_choice=true")
                item("Post-selector continuation: \"use option 1 and apply\" after prior selector -> EXECUTE + requires_creative_choice=false")
            }
            br()
            +"Return JSON only."
            br()

            h2("Output Schema")
            codeblock(
                """
                {
                  "intent_class": "CREATIVE | WRITE | AMBIGUOUS | DESTRUCTIVE",
                  "explicit_write_intent": true,
                  "confidence": 0.0,
                  "evidence_span": "short quote",
                  "reasoning": "short explanation",
                  "resolved_action": "ADVISE | WRITE_CREATE | WRITE_UPDATE | WRITE_DELETE | FOLLOW_UP",
                  "confidence_band": "HIGH | MEDIUM | LOW",
                  "risk_class": "SAFE | DESTRUCTIVE",
                  "anchor_hint": "short target reference",
                  "requires_confirmation": false,
                  "requires_creative_choice": false,
                  "execution_intent": "EXECUTE | INQUIRE"
                }
                """.trimIndent(),
                "json",
            )
        }
    }

    user {
        markdown {
            h3("Recent Context")
            +(recentContext.ifBlank { "(none)" })
            h3("Story Message")
            +userText
        }
    }
}

private fun ResolvedActionLabel.toResolvedAction(): IntentResolvedAction = when (this) {
    ResolvedActionLabel.ADVISE -> IntentResolvedAction.ADVISE
    ResolvedActionLabel.WRITE_CREATE -> IntentResolvedAction.WRITE_CREATE
    ResolvedActionLabel.WRITE_UPDATE -> IntentResolvedAction.WRITE_UPDATE
    ResolvedActionLabel.WRITE_DELETE -> IntentResolvedAction.WRITE_DELETE
    ResolvedActionLabel.FOLLOW_UP -> IntentResolvedAction.FOLLOW_UP
}

private fun ConfidenceBandLabel.toConfidenceBand(): IntentConfidenceBand = when (this) {
    ConfidenceBandLabel.HIGH -> IntentConfidenceBand.HIGH
    ConfidenceBandLabel.MEDIUM -> IntentConfidenceBand.MEDIUM
    ConfidenceBandLabel.LOW -> IntentConfidenceBand.LOW
}

private fun RiskClassLabel.toRiskClass(): IntentRiskClass = when (this) {
    RiskClassLabel.SAFE -> IntentRiskClass.SAFE
    RiskClassLabel.DESTRUCTIVE -> IntentRiskClass.DESTRUCTIVE
}

private fun ExecutionIntentLabel.toExecutionIntent(): IntentExecutionIntent = when (this) {
    ExecutionIntentLabel.EXECUTE -> IntentExecutionIntent.EXECUTE
    ExecutionIntentLabel.INQUIRE -> IntentExecutionIntent.INQUIRE
}
