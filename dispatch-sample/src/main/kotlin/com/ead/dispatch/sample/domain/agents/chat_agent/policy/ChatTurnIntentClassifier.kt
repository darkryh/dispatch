package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.StructureFixingParser
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.PreferencesMemory
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
    @SerialName("preference_confidence")
    val preferenceConfidence: Double = 0.0,
    @SerialName("preference_evidence_span")
    val preferenceEvidenceSpan: String = "",
    @SerialName("preference_reasoning")
    val preferenceReasoning: String = "",
)

suspend fun AIAgentContext.classifyTurnIntentWithAI(request: ChatRequest): ChatIntentSignal {
    if (request.fromDecisionPrompt) {
        return ChatIntentSignal(
            intentClass = ChatIntentClass.WRITE,
            explicitWriteIntent = true,
            confidence = 1.0,
            evidenceSpan = request.text.take(140),
            reasoning = "User answered a decision prompt; continue as write flow.",
            shouldSavePreference = false,
            preferenceConceptKeywords = emptyList(),
            preferenceConfidence = 0.0,
            preferenceEvidenceSpan = "",
            preferenceReasoning = "Decision prompt response should not trigger preference save.",
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
            shouldSavePreference = false,
            preferenceConceptKeywords = emptyList(),
            preferenceConfidence = 0.0,
            preferenceEvidenceSpan = "",
            preferenceReasoning = "",
        )
    }

    val classified = runCatching<ChatIntentClassifierResponse> {
        llm.writeSession {
            val originalPrompt = prompt
            val originalModel = model
            this.model = AIProvider.deepseekChatLlmModel

            try {
                rewritePrompt {
                    chatTurnIntentClassifierPrompt(userText)
                }

                requestLLMStructured<ChatIntentClassifierResponse>(
                    fixingParser = StructureFixingParser(
                        model = AIProvider.deepseekChatLlmModel,
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
            shouldSavePreference = false,
            preferenceConceptKeywords = emptyList(),
            preferenceConfidence = 0.0,
            preferenceEvidenceSpan = "",
            preferenceReasoning = "Classifier failed; skip preference save.",
        )
    }

    val intentClass = when (classified.intentClass) {
        ChatIntentClassifierLabel.CREATIVE -> ChatIntentClass.CREATIVE
        ChatIntentClassifierLabel.WRITE -> ChatIntentClass.WRITE
        ChatIntentClassifierLabel.AMBIGUOUS -> ChatIntentClass.AMBIGUOUS
        ChatIntentClassifierLabel.DESTRUCTIVE -> ChatIntentClass.DESTRUCTIVE
    }

    val allowedPreferenceConcepts = PreferencesMemory.userConcepts
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
        preferenceConfidence = classified.preferenceConfidence.coerceIn(0.0, 1.0),
        preferenceEvidenceSpan = classified.preferenceEvidenceSpan.trim(),
        preferenceReasoning = classified.preferenceReasoning.trim(),
    )
}

private fun chatTurnIntentClassifierPrompt(userText: String): Prompt = prompt("chat-turn-intent-classifier") {
    system {
        markdown {
            h2("Role")
            +"Classify the user's latest message into exactly one chat intent class."
            br()
            +"Return only structured output with intent and preference-save signals."
            br()

            h2("Intent Classes")
            numbered {
                item("CREATIVE: User asks for ideas, feedback, brainstorming, explanation, or advice without asking to save/update/delete data.")
                item("WRITE: User explicitly asks to create, update, save, edit, or otherwise mutate story data in a non-destructive way.")
                item("AMBIGUOUS: Intent is unclear or non-committal (questioning/exploring) and no explicit execute-now mutation request is present.")
                item("DESTRUCTIVE: User explicitly asks to delete, remove, wipe, overwrite, or replace existing data.")
            }
            br()

            h2("Classifier Scope")
            bulleted {
                item("Classify intent only; do not decide final executability.")
                item("Do not require entity IDs or full target resolution to classify WRITE.")
                item("If mutation intent is explicit, classify WRITE even if downstream follow-up may still be needed.")
            }
            br()

            h2("Decision Rules")
            numbered {
                item("If uncertain between classes, choose AMBIGUOUS.")
                item("If message asks for delete/remove/overwrite/replace existing records, choose DESTRUCTIVE.")
                item("If message asks for creation/update with clear explicit write intent and no destructive operation, choose WRITE.")
                item("If user gives corrective mutation instructions (rename/change/set to X) after prior drafts, classify WRITE.")
                item("If message is brainstorming/feedback/creative support without explicit persistence, choose CREATIVE.")
            }
            br()

            h2("Signals to Read")
            bulleted {
                item("Action verbs: create/add/update/edit/save/delete/remove/replace/insert.")
                item("Correction verbs: rename/change/set/switch to.")
                item("Execution-now confirmations: go ahead, do it, create it, proceed, as specified.")
                item("Ideation-only cues: ideas, brainstorm, suggest, options, feedback, explain.")
                item("Ambiguity cues: it/that/this without a clear target, missing required identifiers.")
            }
            br()

            h2("Continuation and Conflict Handling")
            numbered {
                item("If user confirms execution of a previously specified draft (e.g., 'ok create it', 'create it as I specified'), classify as WRITE with explicit_write_intent=true.")
                item("If message contains both style guidance and execution intent (e.g., 'be creative and create one now'), prefer WRITE.")
                item("If wording is noisy/informal/non-native but action+target are clear, still set explicit_write_intent=true.")
                item("If message asks capability only ('can you...?') without clear execute-now intent, choose AMBIGUOUS unless action request is explicit.")
                item("If user uses 'creative' as an adjective for style while requesting creation/update now, classify as WRITE, not CREATIVE.")
                item("If user references earlier output with pronouns ('change it', 'rename him to X', 'set it to Y') and requests mutation now, classify as WRITE.")
            }
            br()

            h2("Write Signal Guidance")
            +"Set explicit_write_intent=true only when user clearly asks to perform a data mutation now."
            br()
            +"Confidence is 0.0 to 1.0:"
            br()
            +"0.85+ very clear, 0.60-0.84 likely, 0.40-0.59 uncertain, below 0.40 unclear."
            br()
            +"For direct execute-now mutation commands (create/update/rename/change/set to X), prefer confidence >= 0.85 unless wording is contradictory."
            br()
            +"evidence_span should quote the short phrase that proves the decision."
            br()

            h2("Preference Save Signal")
            +"Set should_save_preference=true only when the user states durable writing preferences."
            br()
            +"Durable preferences include likes/dislikes or constraints about POV, tense, tone, prose style, and content boundaries."
            br()
            +"Do not save for greetings, task-only execution commands, acknowledgements, or selector answers."
            br()
            +"Allowed preference_concepts values:"
            br()
            +"writer_pov_preference, writer_tense_preference, writer_tone_like_preference, writer_prose_style_preference, writer_content_boundary_preference"
            br()
            +"preference_confidence is 0.0..1.0 and should be high only when wording is explicit."
            br()

            h2("Examples")
            numbered {
                item("`create five new entities` -> WRITE, explicit_write_intent=true")
                item("`create one like this but with a different name, be creative` -> WRITE, explicit_write_intent=true")
                item("`ok create it` -> WRITE, explicit_write_intent=true")
                item("`create it as I specified` -> WRITE, explicit_write_intent=true")
                item("`go ahead and do it now` -> WRITE, explicit_write_intent=true")
                item("`let's change it to the new name` -> WRITE, explicit_write_intent=true")
                item("`rename it to the new title` -> WRITE, explicit_write_intent=true")
                item("`change the name to the updated one` -> WRITE, explicit_write_intent=true")
                item("`update that entry to be shorter` -> WRITE, explicit_write_intent=true")
                item("`update entity id 42 title to Night Route` -> WRITE, explicit_write_intent=true")
                item("`delete the old record` -> DESTRUCTIVE, explicit_write_intent=true")
                item("`replace existing record with this one` -> DESTRUCTIVE, explicit_write_intent=true")
                item("`give me five ideas first` -> CREATIVE, explicit_write_intent=false")
                item("`brainstorm options but don't save anything` -> CREATIVE, explicit_write_intent=false")
                item("`can you create a new one?` -> WRITE, explicit_write_intent=true")
                item("`can you help me decide what to create?` -> CREATIVE, explicit_write_intent=false")
                item("`create a random one` -> WRITE, explicit_write_intent=true")
                item("`should we maybe update this later?` -> AMBIGUOUS, explicit_write_intent=false")
                item("`I prefer first-person present tense` -> CREATIVE, should_save_preference=true, preference_concepts=[writer_pov_preference, writer_tense_preference]")
                item("`please avoid graphic violence` -> CREATIVE, should_save_preference=true, preference_concepts=[writer_content_boundary_preference]")
                item("`thanks, go ahead` -> AMBIGUOUS, should_save_preference=false")
            }
            br()

            h2("Output")
            +"Use this schema:"
            br()
            codeblock(
                """
                {
                  "intent_class": "CREATIVE | WRITE | AMBIGUOUS | DESTRUCTIVE",
                  "explicit_write_intent": true,
                  "confidence": 0.0,
                  "evidence_span": "short quote from user message",
                  "reasoning": "short explanation",
                  "should_save_preference": false,
                  "preference_concepts": ["writer_tone_like_preference"],
                  "preference_confidence": 0.0,
                  "preference_evidence_span": "short quote from user message",
                  "preference_reasoning": "short explanation"
                }
                """.trimIndent(),
                "json",
            )
        }
    }

    user {
        markdown {
            h3("User Message")
            +userText
        }
    }
}
