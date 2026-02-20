package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.SelectorPreferencesMemory
import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import com.ead.dispatch.sample.domain.agents.intent.IntentResolvedAction
import com.ead.dispatch.sample.domain.agents.intent.IntentRiskClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private enum class ChatIntentClassifierLabel {
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
private enum class PreferenceNoveltyLabel {
    @SerialName("NEW")
    NEW,

    @SerialName("ALREADY_KNOWN")
    ALREADY_KNOWN,

    @SerialName("UNCERTAIN")
    UNCERTAIN,
}

@Serializable
private data class ChatIntentClassifierResponse(
    @SerialName("intent_class")
    val intentClass: ChatIntentClassifierLabel,
    @SerialName("explicit_write_intent")
    val explicitWriteIntent: Boolean = false,
    @SerialName("confidence")
    val confidence: Double = 0.0,
    @SerialName("evidence_span")
    val evidenceSpan: String = "",
    @SerialName("reasoning")
    val reasoning: String = "",
    @SerialName("should_save_preference")
    val shouldSavePreference: Boolean = false,
    @SerialName("preference_concepts")
    val preferenceConcepts: List<String> = emptyList(),
    @SerialName("preference_confidence_band")
    val preferenceConfidenceBand: ConfidenceBandLabel = ConfidenceBandLabel.LOW,
    @SerialName("preference_novelty")
    val preferenceNovelty: PreferenceNoveltyLabel = PreferenceNoveltyLabel.UNCERTAIN,
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

suspend fun AIAgentContext.classifyTurnIntentWithAI(request: ChatRequest): ChatIntentSignal {
    if (request.fromDecisionPrompt) {
        return ChatIntentSignal(
            intentClass = ChatIntentClass.WRITE,
            explicitWriteIntent = true,
            confidence = 1.0,
            evidenceSpan = request.text.take(140),
            reasoning = "User answered a decision prompt; continue as write flow.",
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
        return ChatIntentSignal(
            intentClass = ChatIntentClass.AMBIGUOUS,
            explicitWriteIntent = false,
            confidence = 0.0,
            evidenceSpan = "",
            reasoning = "Message is blank.",
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

    val classified = runCatching<ChatIntentClassifierResponse> {
        llm.writeSession {
            val originalPrompt = prompt
            val originalModel = model

            this.model = AIProvider.Chat.intent

            try {
                rewritePrompt {
                    chatTurnIntentClassifierPrompt(userText, recentContext)
                }

                requestLLMStructured<ChatIntentClassifierResponse>(
                    fixingParser = StructureFixingParser(
                        model = AIProvider.Chat.fixer,
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
        return ChatIntentSignal(
            intentClass = ChatIntentClass.AMBIGUOUS,
            explicitWriteIntent = false,
            confidence = 0.0,
            evidenceSpan = userText.take(140),
            reasoning = "Classifier failed; fallback to ambiguous.",
            resolvedAction = IntentResolvedAction.FOLLOW_UP,
            confidenceBand = IntentConfidenceBand.LOW,
            riskClass = IntentRiskClass.SAFE,
            requiresConfirmation = false,
            requiresCreativeChoice = false,
            executionIntent = IntentExecutionIntent.INQUIRE,
        )
    }

    val intentClass = when (classified.intentClass) {
        ChatIntentClassifierLabel.CREATIVE -> ChatIntentClass.CREATIVE
        ChatIntentClassifierLabel.WRITE -> ChatIntentClass.WRITE
        ChatIntentClassifierLabel.AMBIGUOUS -> ChatIntentClass.AMBIGUOUS
        ChatIntentClassifierLabel.DESTRUCTIVE -> ChatIntentClass.DESTRUCTIVE
    }
    val allowedPreferenceConcepts = SelectorPreferencesMemory.userConcepts
        .map { it.keyword.lowercase() }
        .toSet()
    val mappedPreferenceConcepts = classified.preferenceConcepts
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
        .filter { it in allowedPreferenceConcepts }
    val shouldSavePreference = classified.shouldSavePreference && mappedPreferenceConcepts.isNotEmpty()

    return ChatIntentSignal(
        intentClass = intentClass,
        explicitWriteIntent = classified.explicitWriteIntent,
        confidence = classified.confidence.coerceIn(0.0, 1.0),
        evidenceSpan = classified.evidenceSpan.trim(),
        reasoning = classified.reasoning.trim(),
        shouldSavePreference = shouldSavePreference,
        preferenceConceptKeywords = mappedPreferenceConcepts,
        preferenceConfidenceBand = classified.preferenceConfidenceBand.toConfidenceBand(),
        preferenceNovelty = classified.preferenceNovelty.toPreferenceNovelty(),
        resolvedAction = classified.resolvedAction.toResolvedAction(),
        confidenceBand = classified.confidenceBand.toConfidenceBand(),
        riskClass = classified.riskClass.toRiskClass(),
        anchorHint = classified.anchorHint.trim(),
        requiresConfirmation = classified.requiresConfirmation,
        requiresCreativeChoice = classified.requiresCreativeChoice,
        executionIntent = classified.executionIntent.toExecutionIntent(),
    )
}

private fun chatTurnIntentClassifierPrompt(userText: String, recentContext: String): Prompt = prompt("chat-turn-intent-classifier") {
    system {
        markdown {
            h2("Role")
            +"Classify the latest chat turn using language-agnostic, context-aware intent reasoning."
            br()
            h2("Preference Save Signal")
            +"Set should_save_preference=true only for durable selector-style preferences."
            br()
            +"Allowed preference_concepts values:"
            br()
            +"selector_naming_direction_preference, selector_character_direction_preference, selector_plot_direction_preference, selector_tone_direction_preference, selector_general_creative_preference"
            br()
            +"Set preference_confidence_band to HIGH/MEDIUM/LOW."
            br()
            +"Set preference_novelty to NEW when likely new, ALREADY_KNOWN when already present, UNCERTAIN otherwise."
            br()

            +"Interpret intent semantically (not by keywords), resolve continuation using context, and set requires_confirmation=true for destructive/high-risk actions."
            br()
            +"Speech-act precedence: classify by communicative act first, then action semantics."
            br()
            +"If the latest turn is primarily a question, capability check, permission check, hypothetical, comparison, or option-seeking request, set execution_intent=INQUIRE."
            br()
            +"Only set execution_intent=EXECUTE when the user is committing to apply changes now in this turn."
            br()
            +"Set requires_creative_choice=true when execution intent is clear but creative direction selection should remain with the user before persisting changes."
            br()
            +"Do not set requires_creative_choice for simple bounded creates."
            br()
            +"Creative selector decision framework (semantic, not lexical):"
            br()
            numbered {
                item("Direction openness: multiple distinct creative branches are valid and not uniquely implied by constraints.")
                item("Constraint density: role/style/tone/canon-fit requirements are partial, conflicting, or underspecified.")
                item("Canon impact: the decision can materially influence future entities, arcs, voice, or continuity.")
                item("Reversibility risk: choosing one branch now would be costly to undo or likely to trigger rework.")
                item("Delegated choice intent: user is explicitly or implicitly asking the assistant to decide among directions.")
            }
            br()
            +"Trigger requires_creative_choice=true when this framework indicates meaningful branch choice risk and user-direction preference should be preserved."
            br()
            +"If uncertain between direct creative write and creative selector on EXECUTE turns, prefer requires_creative_choice=true."
            br()
            +"Compound-turn precedence:"
            br()
            +"When a single turn both requests create/apply now and also expresses branch-fit uncertainty (style/role/tone/canon compatibility not decided), keep execution_intent=EXECUTE and set requires_creative_choice=true."
            br()
            +"In this compound case, selector comes before persistence; do not collapse into direct write."
            br()
            +"Examples are illustrative, not exhaustive."
            br()
            +"Do not infer creative selector from specific words alone."
            br()
            +"Use recent context to determine whether branch direction is already stabilized."
            br()
            +"Generic request families that may require creative selector:"
            br()
            bulleted {
                item("Foundational entity creation with open fit (protagonists, major antagonists, core factions, governing rules).")
                item("Multi-option generative asks where one persisted direction must be selected.")
                item("Style/voice-alignment asks where current canon could support multiple conflicting directions.")
            }
            br()
            +"Do not trigger creative selector when:"
            br()
            bulleted {
                item("The create/update request is bounded and specific with clear constraints.")
                item("The user already selected direction/path explicitly.")
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
                item("If the user refers to an existing target contextually, prefer WRITE_UPDATE unless delete/overwrite is clear.")
                item("If the action target is unclear, keep execution_intent=INQUIRE and resolved_action=FOLLOW_UP.")
                item("Do not require exact wording to infer continuation intent.")
            }
            br()
            +"Question-type examples (all should be INQUIRE unless user explicitly asks to apply now):"
            br()
            bulleted {
                item("Ability/capability: \"can you create a character like X?\", \"are you able to update chapter 3?\" -> INQUIRE")
                item("Hypothetical: \"what if we changed POV to first person?\" -> INQUIRE")
                item("Comparison/evaluation: \"is this better than before?\" -> INQUIRE")
                item("Option-seeking: \"which option should we pick?\" -> INQUIRE")
                item("Mixed ask-first: \"can you create it, or just tell me first?\" -> INQUIRE")
                item("Shorthand confirmation questions: \"ready?\", \"looks good?\" -> INQUIRE")
                item("Non-English ability forms with same meaning are also INQUIRE (e.g., ES/PT/FR).")
            }
            br()
            +"Additional boundary examples:"
            br()
            bulleted {
                item("Permission + implied action: \"can you update chapter 3 now?\" -> INQUIRE")
                item("Destructive question: \"should we delete this character?\" -> INQUIRE")
                item("Ambiguous shorthand: \"and this one?\", \"same here?\", \"do it?\" -> INQUIRE or FOLLOW_UP if target unclear")
                item("Mixed conflict: \"can you create it? don't apply yet\" -> INQUIRE")
                item("Phased request: \"explain first, then apply\" -> INQUIRE for this turn")
                item("Non-English shorthand inquiry with same meaning (ES/PT/FR) -> INQUIRE")
            }
            br()
            +"Execute examples:"
            br()
            bulleted {
                item("\"create this character now\", \"save this update\", \"delete this entry now\" -> EXECUTE")
                item("Short continuation acknowledgements after a prepared write plan/decision -> EXECUTE")
                item("\"proceed\", \"yes apply\" after selector/decision prompt -> EXECUTE")
                item("Destructive explicit command: \"delete this entry now\" -> EXECUTE with destructive risk/confirmation")
                item("Continuation commit after prepared target: \"let's go ahead\", \"apply it\", \"continue\" -> EXECUTE")
            }
            br()
            +"Creative selector examples:"
            br()
            bulleted {
                item("Execute + under-constrained direction: \"create a new lead now; decide which role/style best fits the current cast\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + open branching: \"add 2-3 antagonist options and pick the best tone for our current arc\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + compatibility uncertainty: \"create a major character now; uncertain which voice and function should align with current canon\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + delegated fit decision: \"add a core ally and decide the best role fit for current arcs\" -> EXECUTE + requires_creative_choice=true")
                item("Bounded create: \"create three random side characters\" -> EXECUTE + requires_creative_choice=false")
                item("Precise create: \"create a 19-year-old medic ally named Lina, optimistic tone\" -> EXECUTE + requires_creative_choice=false")
            }
            br()
            +"Additional contrastive examples:"
            br()
            bulleted {
                item("Early context bootstrap: \"create one random character for a fresh story\" -> EXECUTE + requires_creative_choice=false")
                item("Mature canon with open fit: \"add a new protagonist and decide how they should integrate with existing arcs\" -> EXECUTE + requires_creative_choice=true")
                item("Bounded profile despite create-now: \"create a strategist mentor, age 40s, reserved tone, supports arc B\" -> EXECUTE + requires_creative_choice=false")
                item("Compound mixed signal: \"create now, but I'm not sure which direction best fits\" -> EXECUTE + requires_creative_choice=true")
                item("Multi-entity coupling: \"create a character and matching faction direction; choose what best fits current world rules\" -> EXECUTE + requires_creative_choice=true")
                item("Post-selector continuation: \"apply option 2\" after prior selector -> EXECUTE + requires_creative_choice=false")
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
                  "should_save_preference": false,
                  "preference_concepts": ["selector_general_creative_preference"],
                  "preference_confidence_band": "HIGH | MEDIUM | LOW",
                  "preference_novelty": "NEW | ALREADY_KNOWN | UNCERTAIN",
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
            h3("User Message")
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

private fun PreferenceNoveltyLabel.toPreferenceNovelty(): ChatPreferenceNovelty = when (this) {
    PreferenceNoveltyLabel.NEW -> ChatPreferenceNovelty.NEW
    PreferenceNoveltyLabel.ALREADY_KNOWN -> ChatPreferenceNovelty.ALREADY_KNOWN
    PreferenceNoveltyLabel.UNCERTAIN -> ChatPreferenceNovelty.UNCERTAIN
}
