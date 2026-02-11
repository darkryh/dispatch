package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

class ChatModeReplayValidationTest {

    @Test
    fun `chat mode replay suite meets release KPI gates`() {
        val dataset = replayDataset()
        assertTrue(
            dataset.size == REQUIRED_REPLAY_CASE_COUNT,
            "Replay dataset size changed: expected=$REQUIRED_REPLAY_CASE_COUNT actual=${dataset.size}",
        )
        val result = evaluateReplayCases(dataset)

        val report = result.render()
        println(report)

        assertTrue(
            result.writeIntentPrecision >= WRITE_INTENT_PRECISION_MIN,
            "Write-intent precision gate failed.\n$report",
        )
        assertTrue(
            result.selectorPrecision >= SELECTOR_PRECISION_MIN,
            "Selector precision gate failed.\n$report",
        )
        assertTrue(
            result.selectorRecall >= SELECTOR_RECALL_MIN,
            "Selector recall gate failed.\n$report",
        )
        assertTrue(
            result.wrongWriteRate <= WRONG_WRITE_RATE_MAX,
            "Wrong-write rate gate failed.\n$report",
        )
    }

    private fun evaluateReplayCases(cases: List<ReplayCase>): ReplayKpiResult {
        var writeTruePositive = 0
        var writeFalsePositive = 0
        var selectorTruePositive = 0
        var selectorFalsePositive = 0
        var selectorFalseNegative = 0
        var wrongWrites = 0
        var totalTurns = 0
        var totalEstimatedInputTokens = 0
        var totalEstimatedOutputTokens = 0

        val mismatches = mutableListOf<String>()

        cases.forEach { replayCase ->
            val request = ChatRequest(
                text = replayCase.text,
                storyId = "validation-story",
                fromDecisionPrompt = replayCase.fromDecisionPrompt,
            )
            val policy = buildTurnPolicy(
                request = request,
                intentSignal = replayCase.signal,
            )
            val observedOutcome = observedOutcomeFor(policy)

            if (observedOutcome != replayCase.expectedOutcome) {
                mismatches += "${replayCase.id} [${replayCase.category}] expected=${replayCase.expectedOutcome} observed=$observedOutcome text=\"${replayCase.text}\""
            }

            val predictedWrite = observedOutcome == ExpectedOutcome.WRITE
            val actualWrite = replayCase.expectedOutcome == ExpectedOutcome.WRITE
            if (predictedWrite && actualWrite) writeTruePositive += 1
            if (predictedWrite && !actualWrite) {
                writeFalsePositive += 1
                wrongWrites += 1
            }

            val predictedSelector = observedOutcome == ExpectedOutcome.SELECTOR
            val actualSelector = replayCase.expectedOutcome == ExpectedOutcome.SELECTOR
            if (predictedSelector && actualSelector) selectorTruePositive += 1
            if (predictedSelector && !actualSelector) selectorFalsePositive += 1
            if (!predictedSelector && actualSelector) selectorFalseNegative += 1

            totalTurns += turnsToComplete(observedOutcome)

            totalEstimatedInputTokens += estimateTokens(replayCase.text) + CLASSIFIER_INPUT_OVERHEAD_TOKENS
            totalEstimatedOutputTokens += estimatedOutputTokens(observedOutcome)
        }

        val writeIntentPrecision = precision(writeTruePositive, writeFalsePositive)
        val selectorPrecision = precision(selectorTruePositive, selectorFalsePositive)
        val selectorRecall = recall(selectorTruePositive, selectorFalseNegative)
        val wrongWriteRate = ratio(wrongWrites, cases.size)
        val averageTurnsToComplete = ratio(totalTurns, cases.size)
        val averageEstimatedTokensPerTask = ratio(totalEstimatedInputTokens + totalEstimatedOutputTokens, cases.size)
        val averageEstimatedCostUnits = ratio(
            (totalEstimatedInputTokens * COST_WEIGHT_INPUT) + (totalEstimatedOutputTokens * COST_WEIGHT_OUTPUT),
            cases.size,
        )

        return ReplayKpiResult(
            totalCases = cases.size,
            writeIntentPrecision = writeIntentPrecision,
            selectorPrecision = selectorPrecision,
            selectorRecall = selectorRecall,
            wrongWriteRate = wrongWriteRate,
            averageTurnsToCompleteTask = averageTurnsToComplete,
            averageEstimatedTokensPerTask = averageEstimatedTokensPerTask,
            averageEstimatedCostUnitsPerTask = averageEstimatedCostUnits,
            mismatchSamples = mismatches.take(12),
        )
    }

    private fun observedOutcomeFor(policy: ChatTurnPolicy): ExpectedOutcome {
        return when {
            policy.decisionPath == ChatDecisionPath.SELECTOR -> ExpectedOutcome.SELECTOR
            isToolAllowedForTurn(policy, "createCharacter") -> ExpectedOutcome.WRITE
            policy.decisionPath == ChatDecisionPath.FOLLOW_UP -> ExpectedOutcome.FOLLOW_UP
            else -> ExpectedOutcome.NO_WRITE
        }
    }

    private fun turnsToComplete(outcome: ExpectedOutcome): Int =
        when (outcome) {
            ExpectedOutcome.NO_WRITE,
            ExpectedOutcome.WRITE,
            -> 1

            ExpectedOutcome.SELECTOR,
            ExpectedOutcome.FOLLOW_UP,
            -> 2
        }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length / 4.0).roundToInt().coerceAtLeast(1)
    }

    private fun estimatedOutputTokens(outcome: ExpectedOutcome): Int =
        when (outcome) {
            ExpectedOutcome.NO_WRITE -> 42
            ExpectedOutcome.WRITE -> 34
            ExpectedOutcome.SELECTOR -> 26
            ExpectedOutcome.FOLLOW_UP -> 22
        }

    private fun precision(tp: Int, fp: Int): Double = if (tp + fp == 0) 1.0 else tp.toDouble() / (tp + fp)

    private fun recall(tp: Int, fn: Int): Double = if (tp + fn == 0) 1.0 else tp.toDouble() / (tp + fn)

    private fun ratio(value: Int, total: Int): Double = if (total == 0) 0.0 else value.toDouble() / total

    private fun ratio(value: Double, total: Int): Double = if (total == 0) 0.0 else value / total

    private fun replayDataset(): List<ReplayCase> = buildList {
        addCreativeCases()
        addWriteCases()
        addFollowUpCases()
        addSelectorCases()
        addDecisionContinuationCases()
    }

    private fun MutableList<ReplayCase>.addCreativeCases() {
        val prompts = listOf(
            "give me 5 character ideas",
            "brainstorm two possible endings",
            "what genre fits this premise",
            "help me improve this dialogue tone",
            "suggest location vibes for a lonely city",
            "i want options before deciding",
            "can you review this arc for pacing",
            "what themes can we explore here",
            "give me feedback, do not save",
            "explain why this scene feels flat",
            "propose names but don't create yet",
            "quiero ideas para un personaje triste",
            "dame opciones de tono narrativo",
            "quais ideias para um anti-heroi espacial",
            "could you suggest but not persist anything",
            "help me decide what to create first",
            "what do you think about this concept",
            "i prefer first person present tense",
            "please avoid graphic violence",
            "i like melancholic and sparse prose",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "creative-${index + 1}",
                    category = "creative",
                    text = text,
                    expectedOutcome = ExpectedOutcome.NO_WRITE,
                    signal = ChatIntentSignal(
                        intentClass = ChatIntentClass.CREATIVE,
                        explicitWriteIntent = false,
                        confidence = 0.88,
                        evidenceSpan = text.take(40),
                        reasoning = "Ideation/advice request.",
                        shouldSavePreference = text.contains("prefer") || text.contains("avoid") || text.contains("like"),
                        preferenceConceptKeywords = when {
                            text.contains("first person") -> listOf("writer_pov_preference", "writer_tense_preference")
                            text.contains("avoid graphic violence") -> listOf("writer_content_boundary_preference")
                            text.contains("melancholic") -> listOf("writer_tone_like_preference", "writer_prose_style_preference")
                            else -> emptyList()
                        },
                        preferenceConfidence = 0.82,
                        preferenceEvidenceSpan = text.take(40),
                        preferenceReasoning = "Durable preference signal when present.",
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addWriteCases() {
        val directWritePrompts = listOf(
            "create a random character",
            "create five new characters",
            "add a location called rust harbor",
            "update character id 42 with a shorter description",
            "set story genre to space noir",
            "insert a timeline entry called first contact",
            "upsert organization called eclipse union",
            "create one like this with a different name",
            "create it as i specified",
            "ok create it",
            "go ahead and create now",
            "please save this character as kaito ren",
            "add two world rules about memory loss",
            "create an arc titled falling orbit",
            "update location neon bazaar tags to black market",
            "cria um personagem novo agora",
            "crea un personaje ahora",
            "ajoute un nouveau personnage maintenant",
        )

        directWritePrompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "write-direct-${index + 1}",
                    category = "write",
                    text = text,
                    expectedOutcome = ExpectedOutcome.WRITE,
                    signal = ChatIntentSignal(
                        intentClass = ChatIntentClass.WRITE,
                        explicitWriteIntent = true,
                        confidence = 0.91,
                        evidenceSpan = text.take(48),
                        reasoning = "Explicit mutate-now request.",
                    ),
                )
            )
        }

        val noisyButClearWritePrompts = listOf(
            "you can do whatever you want create them now",
            "be creative and create one now",
            "like spike spiegel vibe but create on your own now",
            "make 3 random chars and save",
            "i said create it please do it now",
            "can u create one rn",
        )

        noisyButClearWritePrompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "write-noisy-${index + 1}",
                    category = "write",
                    text = text,
                    expectedOutcome = ExpectedOutcome.WRITE,
                    signal = ChatIntentSignal(
                        intentClass = ChatIntentClass.AMBIGUOUS,
                        explicitWriteIntent = true,
                        confidence = 0.79,
                        evidenceSpan = text.take(48),
                        reasoning = "Noisy wording, but execute-now action is clear.",
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addFollowUpCases() {
        val prompts = listOf(
            "maybe update this",
            "change it",
            "do something with that character",
            "can you create one?",
            "what should we edit first",
            "update that entry",
            "im not sure maybe save maybe not",
            "do it if needed",
            "could be better somehow",
            "set this one to that style",
            "make it like before",
            "tweak the old one",
            "can you maybe handle this",
            "maybe remove something maybe not",
            "idk do what you think",
            "help with this quickly",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "follow-up-${index + 1}",
                    category = "ambiguous",
                    text = text,
                    expectedOutcome = ExpectedOutcome.FOLLOW_UP,
                    signal = ChatIntentSignal(
                        intentClass = ChatIntentClass.AMBIGUOUS,
                        explicitWriteIntent = index % 4 == 0,
                        confidence = 0.31,
                        evidenceSpan = text.take(36),
                        reasoning = "Target or action is unclear.",
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addSelectorCases() {
        val prompts = listOf(
            "delete the old location",
            "remove character id 12",
            "wipe all timeline entries",
            "replace existing rule with this one",
            "overwrite current arc with new text",
            "clear all cultures",
            "drop previous story settings",
            "delete duplicate character",
            "remove the relationship record",
            "replace the saved version",
            "delete this and recreate",
            "clear world rules list",
            "remove old draft permanently",
            "overwrite current title and discard history",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "selector-${index + 1}",
                    category = "destructive",
                    text = text,
                    expectedOutcome = ExpectedOutcome.SELECTOR,
                    signal = ChatIntentSignal(
                        intentClass = ChatIntentClass.DESTRUCTIVE,
                        explicitWriteIntent = true,
                        confidence = 0.96,
                        evidenceSpan = text.take(40),
                        reasoning = "Destructive request must confirm first.",
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addDecisionContinuationCases() {
        val prompts = listOf(
            "yes proceed",
            "ok do it",
            "confirm replace existing",
            "continue with option 2",
            "approved, execute now",
            "go ahead with selected option",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "decision-${index + 1}",
                    category = "decision-continuation",
                    text = text,
                    expectedOutcome = ExpectedOutcome.WRITE,
                    fromDecisionPrompt = true,
                    signal = ChatIntentSignal(
                        intentClass = ChatIntentClass.CREATIVE,
                        explicitWriteIntent = false,
                        confidence = 0.12,
                        evidenceSpan = text.take(30),
                        reasoning = "Ignored because fromDecisionPrompt=true path bypasses classifier intent.",
                    ),
                )
            )
        }
    }

    private fun replayCase(
        id: String,
        category: String,
        text: String,
        expectedOutcome: ExpectedOutcome,
        signal: ChatIntentSignal,
        fromDecisionPrompt: Boolean = false,
    ): ReplayCase = ReplayCase(
        id = id,
        category = category,
        text = text,
        expectedOutcome = expectedOutcome,
        signal = signal,
        fromDecisionPrompt = fromDecisionPrompt,
    )

    private data class ReplayCase(
        val id: String,
        val category: String,
        val text: String,
        val expectedOutcome: ExpectedOutcome,
        val signal: ChatIntentSignal,
        val fromDecisionPrompt: Boolean = false,
    )

    private enum class ExpectedOutcome {
        NO_WRITE,
        WRITE,
        SELECTOR,
        FOLLOW_UP,
    }

    private data class ReplayKpiResult(
        val totalCases: Int,
        val writeIntentPrecision: Double,
        val selectorPrecision: Double,
        val selectorRecall: Double,
        val wrongWriteRate: Double,
        val averageTurnsToCompleteTask: Double,
        val averageEstimatedTokensPerTask: Double,
        val averageEstimatedCostUnitsPerTask: Double,
        val mismatchSamples: List<String>,
    ) {
        fun render(): String {
            val mismatchBlock = if (mismatchSamples.isEmpty()) {
                "(none)"
            } else {
                mismatchSamples.joinToString(separator = "\n")
            }

            return buildString {
                appendLine("Chat Mode Replay Validation")
                appendLine("- cases: $totalCases")
                appendLine("- write_intent_precision: ${format(writeIntentPrecision)} (gate >= ${format(WRITE_INTENT_PRECISION_MIN)})")
                appendLine("- selector_precision: ${format(selectorPrecision)} (gate >= ${format(SELECTOR_PRECISION_MIN)})")
                appendLine("- selector_recall: ${format(selectorRecall)} (gate >= ${format(SELECTOR_RECALL_MIN)})")
                appendLine("- wrong_write_rate: ${format(wrongWriteRate)} (gate <= ${format(WRONG_WRITE_RATE_MAX)})")
                appendLine("- avg_turns_to_complete_task: ${format(averageTurnsToCompleteTask)}")
                appendLine("- avg_estimated_tokens_per_task: ${format(averageEstimatedTokensPerTask)}")
                appendLine("- avg_estimated_cost_units_per_task: ${format(averageEstimatedCostUnitsPerTask)}")
                appendLine("- mismatches:")
                appendLine(mismatchBlock)
            }
        }

        private fun format(value: Double): String = "%.4f".format(value)
    }

    companion object {
        private const val WRITE_INTENT_PRECISION_MIN = 0.95
        private const val SELECTOR_PRECISION_MIN = 0.90
        private const val SELECTOR_RECALL_MIN = 0.95
        private const val WRONG_WRITE_RATE_MAX = 0.01
        private const val REQUIRED_REPLAY_CASE_COUNT = 80

        private const val CLASSIFIER_INPUT_OVERHEAD_TOKENS = 64

        // Cost units are deterministic proxies used for trend comparison between revisions.
        private const val COST_WEIGHT_INPUT = 1.0
        private const val COST_WEIGHT_OUTPUT = 3.0
    }
}
