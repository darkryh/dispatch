package com.ead.dispatch.sample.domain.agents.story_agent.policy

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.StructureFixingParser
import com.ead.dispatch.sample.domain.AIProvider
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
)

suspend fun AIAgentContext.classifyStoryTurnIntentWithAI(request: StoryRequest): StoryIntentSignal {
    if (request.fromDecisionPrompt) {
        return StoryIntentSignal(
            intentClass = StoryIntentClass.WRITE,
            explicitWriteIntent = true,
            confidence = 1.0,
            evidenceSpan = request.text.take(140),
            reasoning = "User answered a story decision prompt; continue as write flow.",
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
        )
    }

    val classified = runCatching<StoryIntentClassifierResponse> {
        llm.writeSession {
            val originalPrompt = prompt
            val originalModel = model
            val usageModel = AIProvider.Story.intent
            this.model = usageModel

            try {
                rewritePrompt {
                    storyTurnIntentClassifierPrompt(userText)
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
    )
}

private fun storyTurnIntentClassifierPrompt(userText: String): Prompt = prompt("story-turn-intent-classifier") {
    system {
        markdown {
            h2("Role")
            +"Classify the user's latest STORY mode message into exactly one intent class."
            br()
            +"Return only structured output."
            br()

            h2("Story Mode Scope")
            bulleted {
                item("Story mode is for volumes, chapters, scenes, and progressive narrative drafting.")
                item("Chat mode handles worldbuilding entities; do not treat those as story writes unless user asks story-structure mutation now.")
            }
            br()

            h2("Intent Classes")
            numbered {
                item("CREATIVE: Brainstorming/advisory/storycraft feedback without explicit save/update/delete now.")
                item("WRITE: Explicit create/update/edit/set operations for volume/chapter/scene/story draft data.")
                item("AMBIGUOUS: Intent unclear or non-committal; missing clear execute-now request.")
                item("DESTRUCTIVE: Explicit delete/remove/wipe/replace existing story structure/content.")
            }
            br()

            h2("Rules")
            numbered {
                item("If user asks to create/continue/rewrite chapter or scene now, classify WRITE.")
                item("If user asks to remove/delete volume/chapter/scene or overwrite existing draft, classify DESTRUCTIVE.")
                item("If user asks for options only, critique, or planning without persistence, classify CREATIVE.")
                item("If uncertain, classify AMBIGUOUS.")
            }
            br()

            h2("Output")
            codeblock(
                """
                {
                  "intent_class": "CREATIVE | WRITE | AMBIGUOUS | DESTRUCTIVE",
                  "explicit_write_intent": true,
                  "confidence": 0.0,
                  "evidence_span": "short quote from user message",
                  "reasoning": "short explanation"
                }
                """.trimIndent(),
                "json",
            )
        }
    }

    user {
        markdown {
            h3("Story Message")
            +userText
        }
    }
}
