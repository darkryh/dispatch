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
    @SerialName("decision_before_persist")
    val decisionBeforePersist: Boolean = false,
    @SerialName("execution_intent")
    val executionIntent: ExecutionIntentLabel = ExecutionIntentLabel.INQUIRE,
)

suspend fun AIAgentContext.classifyTurnIntentWithAI(request: ChatRequest): ChatIntentSignal {
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
            decisionBeforePersist = false,
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
                    chatTurnIntentClassifierPrompt(request, userText, recentContext)
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
            decisionBeforePersist = false,
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

    val signal = ChatIntentSignal(
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
        decisionBeforePersist = classified.decisionBeforePersist,
        executionIntent = classified.executionIntent.toExecutionIntent(),
    )

    return applySelectorDecisionIntentSubtype(request, signal)
}

private fun chatTurnIntentClassifierPrompt(
    request: ChatRequest,
    userText: String,
    recentContext: String,
): Prompt = prompt("chat-turn-intent-classifier") {
    system {
        markdown {
            h2("Role")
            +"Classify the latest chat turn using language-agnostic, context-aware intent reasoning."
            br()
            h2("Preference Save Signal")
            +"Set should_save_preference=true only for durable chat readability preferences."
            br()
            +"Allowed preference_concepts values:"
            br()
            +"chat_readability_preference"
            br()
            +"Set preference_confidence_band to HIGH/MEDIUM/LOW."
            br()
            +"Set preference_novelty to NEW when likely new, ALREADY_KNOWN when already present, UNCERTAIN otherwise."
            br()
            +"For decision-prompt turns, use decision context to determine if the chosen option reflects durable user preference."
            br()
            +"For non-decision turns, keep should_save_preference=false unless durable preference is explicitly clear."
            br()

            +"Interpret intent semantically (not by keywords), resolve continuation using context, and set requires_confirmation=true for destructive/high-risk actions."
            br()
            +"Decision procedure (must follow in order):"
            br()
            numbered {
                item("Determine speech act / execution intent first (INQUIRE vs EXECUTE) from the current turn plus recent workflow context.")
                item("Determine write resolution (create/update/delete/advice/follow-up) independently of selector choice.")
                item("Then evaluate branch-decision risk before persistence (selector need) for EXECUTE turns only.")
                item("If branch-decision risk is meaningful, selector-first takes precedence over autonomous direct write.")
            }
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
            +"Delegated-fit continuation rule:"
            br()
            +"When recent context shows prior creative generation/synthesis and the user asks to create another foundational element while delegating fit (e.g., 'best fit', 'what fits best', 'you decide'), treat this as selector-worthy branching rather than direct autonomous selection."
            br()
            +"In delegated-fit continuation cases, keep execution_intent=EXECUTE and set requires_creative_choice=true even if the user does not explicitly say the decision is important."
            br()
            +"If persistence/apply-now is implied by the create request and the branch should be chosen before writing, set decision_before_persist=true."
            br()
            +"Priority conflict rule:"
            br()
            +"When both statements are true: (A) the user delegates fit/direction to the assistant, and (B) multiple story-shaping branches remain plausible, DO NOT collapse to direct autonomous creation. Mark selector-first (requires_creative_choice=true)."
            br()
            +"Delegated fit means the assistant may evaluate options; it does not automatically mean the assistant should silently choose one and persist it."
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
            +"Selector branch-risk scoring guide (semantic, not lexical):"
            br()
            bulleted {
                item("Low: bounded utility/minor element; branch choice has limited downstream canon impact.")
                item("Medium: multiple plausible fits exist and choice changes local scene/relationship dynamics.")
                item("High: foundational element or decision materially shifts future arcs, cast balance, world behavior, or continuity commitments.")
            }
            br()
            +"For EXECUTE + WRITE_CREATE turns with delegated fit and medium/high branch-risk, prefer selector-first."
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
            +"If user explicitly asks for ideas/options without saving/applying yet, classify as INQUIRE and keep requires_creative_choice=false."
            br()
            +"Set execution_intent=EXECUTE only when user clearly requests to apply/save/create/update/delete now."
            br()
            +"Decision continuation messages after a selector prompt should be EXECUTE."
            br()
            +"Continuation assent after assistant-proposed action should be EXECUTE when the user is approving/proceeding in context."
            br()
            +"Do not require command-form phrasing; approvals, go-ahead confirmations, and collaborative continuation language can still be EXECUTE."
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
                item("When prior assistant context presents a concrete write plan and the user gives contextual approval, treat as EXECUTE unless the user defers/asks-only.")
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
                item("Option ideation without persistence: \"propose options without saving yet\" -> INQUIRE + requires_creative_choice=false")
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
                item("Collaborative continuation assent after assistant proposes concrete creation/update -> EXECUTE")
                item("Contextual approval turns (approve/confirm/go ahead) tied to an identified target in recent context -> EXECUTE")
            }
            br()
            +"Creative selector examples:"
            br()
            bulleted {
                item("Execute + under-constrained direction: \"create a new lead now; decide which role/style best fits the current cast\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + open branching: \"add 2-3 antagonist options and pick the best tone for our current arc\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + compatibility uncertainty: \"create a major character now; uncertain which voice and function should align with current canon\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + delegated fit decision: \"add a core ally and decide the best role fit for current arcs\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + ordering constraint: \"create and choose direction before saving\" -> EXECUTE + requires_creative_choice=true")
                item("Execute + directional dependency: \"add/create entity, decide narrative direction first, then save\" -> EXECUTE + requires_creative_choice=true + decision_before_persist=true")
                item("Continuation after prior cast generation: \"can you create another protagonist and decide what's the best fit for it?\" -> EXECUTE + requires_creative_choice=true (and decision_before_persist=true if persistence is implied)")
                item("Continuation after failed-fix context: \"can you create the anchoring/stabilizer artifact and decide what's the best fit for it?\" -> EXECUTE + requires_creative_choice=true (and decision_before_persist=true if persistence is implied)")
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
                item("Low-impact utility create: \"create and save a small dock token item traders use for storage marks\" -> EXECUTE + requires_creative_choice=false")
                item("Low-impact one-scene utility artifact: \"create and save a small maintenance tool artifact for relay workers\" -> EXECUTE + requires_creative_choice=false")
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
                  "preference_concepts": ["chat_readability_preference"],
                  "preference_confidence_band": "HIGH | MEDIUM | LOW",
                  "preference_novelty": "NEW | ALREADY_KNOWN | UNCERTAIN",
                  "resolved_action": "ADVISE | WRITE_CREATE | WRITE_UPDATE | WRITE_DELETE | FOLLOW_UP",
                  "confidence_band": "HIGH | MEDIUM | LOW",
                  "risk_class": "SAFE | DESTRUCTIVE",
                  "anchor_hint": "short target reference",
                  "requires_confirmation": false,
                  "requires_creative_choice": false,
                  "decision_before_persist": false,
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
            h3("Turn Metadata")
            +"from_decision_prompt: ${request.fromDecisionPrompt}"
            selectorDecisionMetadata(request.decisionContext).forEach { +it }
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
