package com.ead.dispatch.sample.domain.agents.story_agent.policy

import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import com.ead.dispatch.sample.domain.agents.intent.IntentResolvedAction
import com.ead.dispatch.sample.domain.agents.intent.IntentRiskClass
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

class StoryModeReplayValidationTest {

    @Test
    fun `story mode replay suite meets release KPI gates`() {
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
            val request = StoryRequest(
                text = replayCase.text,
                storyId = "validation-story",
                fromDecisionPrompt = replayCase.fromDecisionPrompt,
            )
            val policy = buildStoryTurnPolicy(
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

    private fun observedOutcomeFor(policy: StoryTurnPolicy): ExpectedOutcome {
        return when {
            policy.decisionPath == StoryDecisionPath.SELECTOR -> ExpectedOutcome.SELECTOR
            isStoryToolAllowedForTurn(policy, "setChapterDraft") -> ExpectedOutcome.WRITE
            policy.decisionPath == StoryDecisionPath.FOLLOW_UP -> ExpectedOutcome.FOLLOW_UP
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
            ExpectedOutcome.NO_WRITE -> 40
            ExpectedOutcome.WRITE -> 36
            ExpectedOutcome.SELECTOR -> 26
            ExpectedOutcome.FOLLOW_UP -> 20
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
        addInquirySafetyCases()
        addDecisionContinuationCases()
        addBootstrapContinuationCases()
    }

    private fun MutableList<ReplayCase>.addCreativeCases() {
        val prompts = listOf(
            "give me three scene ideas",
            "brainstorm chapter pacing options",
            "help me review this chapter draft",
            "explain why this scene feels weak",
            "suggest alternative opening paragraphs",
            "quiero ideas para el siguiente capitulo",
            "me de ideias para a cena final",
            "donne moi des options de narration",
            "help me plan, do not apply changes",
            "what direction should volume two take",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "creative-${index + 1}",
                    category = "creative",
                    text = text,
                    expectedOutcome = ExpectedOutcome.NO_WRITE,
                    signal = StoryIntentSignal(
                        intentClass = StoryIntentClass.CREATIVE,
                        explicitWriteIntent = false,
                        confidence = 0.86,
                        evidenceSpan = text.take(42),
                        reasoning = "Advisory storycraft request.",
                        resolvedAction = IntentResolvedAction.ADVISE,
                        confidenceBand = IntentConfidenceBand.MEDIUM,
                        riskClass = IntentRiskClass.SAFE,
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addWriteCases() {
        val directWritePrompts = listOf(
            "create chapter 3 in volume 1",
            "update scene 2 summary",
            "continue writing this chapter draft",
            "set chapter draft with this content",
            "apply the pending chapter proposal",
            "add a new volume outline",
            "actualiza el capitulo actual",
            "continua a escrita desta cena",
            "mise a jour du chapitre maintenant",
            "proceed with the approved draft",
        )

        directWritePrompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "write-${index + 1}",
                    category = "write",
                    text = text,
                    expectedOutcome = ExpectedOutcome.WRITE,
                    signal = StoryIntentSignal(
                        intentClass = StoryIntentClass.WRITE,
                        explicitWriteIntent = true,
                        confidence = 0.9,
                        evidenceSpan = text.take(48),
                        reasoning = "Direct story mutation request.",
                        resolvedAction = IntentResolvedAction.WRITE_UPDATE,
                        confidenceBand = IntentConfidenceBand.HIGH,
                        riskClass = IntentRiskClass.SAFE,
                        anchorHint = "chapter",
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addFollowUpCases() {
        val prompts = listOf(
            "maybe change something",
            "do something with this",
            "can you handle this chapter",
            "not sure yet",
            "improve it",
            "haz algo con eso",
            "faz alguma coisa com isso",
            "peut etre modifier",
            "i am undecided",
            "later maybe",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "follow-up-${index + 1}",
                    category = "ambiguous",
                    text = text,
                    expectedOutcome = ExpectedOutcome.FOLLOW_UP,
                    signal = StoryIntentSignal(
                        intentClass = StoryIntentClass.AMBIGUOUS,
                        explicitWriteIntent = false,
                        confidence = 0.28,
                        evidenceSpan = text.take(36),
                        reasoning = "Insufficiently anchored request.",
                        resolvedAction = IntentResolvedAction.FOLLOW_UP,
                        confidenceBand = IntentConfidenceBand.LOW,
                        riskClass = IntentRiskClass.SAFE,
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addSelectorCases() {
        val prompts = listOf(
            "delete chapter 4",
            "remove this volume",
            "overwrite the current draft",
            "replace chapter draft entirely",
            "wipe the previous scene",
            "elimina el capitulo anterior",
            "apaga o volume antigo",
            "supprime cette scene",
            "clear current chapter draft",
            "discard and replace existing text",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "selector-${index + 1}",
                    category = "destructive",
                    text = text,
                    expectedOutcome = ExpectedOutcome.SELECTOR,
                    signal = StoryIntentSignal(
                        intentClass = StoryIntentClass.DESTRUCTIVE,
                        explicitWriteIntent = true,
                        confidence = 0.94,
                        evidenceSpan = text.take(40),
                        reasoning = "Destructive story operation.",
                        resolvedAction = IntentResolvedAction.WRITE_DELETE,
                        confidenceBand = IntentConfidenceBand.HIGH,
                        riskClass = IntentRiskClass.DESTRUCTIVE,
                        requiresConfirmation = true,
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addDecisionContinuationCases() {
        val prompts = listOf(
            "yes continue",
            "approved",
            "proceed",
            "ok apply",
            "confirm selection",
            "continue with chosen option",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "decision-${index + 1}",
                    category = "decision-continuation",
                    text = text,
                    expectedOutcome = ExpectedOutcome.WRITE,
                    fromDecisionPrompt = true,
                    signal = StoryIntentSignal(
                        intentClass = StoryIntentClass.CREATIVE,
                        explicitWriteIntent = false,
                        confidence = 0.1,
                        evidenceSpan = text.take(30),
                        reasoning = "Bypassed due to decision continuation.",
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addBootstrapContinuationCases() {
        val prompts = listOf(
            "let's start chapter 1 now",
            "begin the first chapter and apply it",
            "create volume one and first chapter now",
            "start chapter one with a short opening scene now",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "bootstrap-${index + 1}",
                    category = "bootstrap",
                    text = text,
                    expectedOutcome = ExpectedOutcome.WRITE,
                    signal = StoryIntentSignal(
                        intentClass = StoryIntentClass.WRITE,
                        explicitWriteIntent = true,
                        confidence = 0.9,
                        evidenceSpan = text.take(48),
                        reasoning = "Bootstrap chapter execution request.",
                        resolvedAction = IntentResolvedAction.WRITE_CREATE,
                        confidenceBand = IntentConfidenceBand.HIGH,
                        riskClass = IntentRiskClass.SAFE,
                        executionIntent = IntentExecutionIntent.EXECUTE,
                    ),
                )
            )
        }
    }

    private fun MutableList<ReplayCase>.addInquirySafetyCases() {
        val prompts = listOf(
            "can you create a chapter like this style?",
            "could you update this scene now?",
            "is this chapter draft better?",
            "should we delete this volume?",
            "what if we rewrite this scene in first person?",
            "can you do it or just explain first?",
            "and this chapter?",
            "looks ready?",
            "puedes crear un capitulo asi?",
            "voce consegue atualizar essa cena?",
        )

        prompts.forEachIndexed { index, text ->
            add(
                replayCase(
                    id = "inquiry-safety-${index + 1}",
                    category = "inquiry-safety",
                    text = text,
                    expectedOutcome = ExpectedOutcome.NO_WRITE,
                    signal = StoryIntentSignal(
                        intentClass = when (index) {
                            2, 4, 7 -> StoryIntentClass.CREATIVE
                            6 -> StoryIntentClass.AMBIGUOUS
                            else -> StoryIntentClass.WRITE
                        },
                        explicitWriteIntent = index !in listOf(2, 4, 7),
                        confidence = 0.84,
                        evidenceSpan = text.take(48),
                        reasoning = "Inquiry turn should answer only and avoid write execution.",
                        resolvedAction = IntentResolvedAction.WRITE_UPDATE,
                        confidenceBand = IntentConfidenceBand.MEDIUM,
                        riskClass = IntentRiskClass.SAFE,
                        executionIntent = IntentExecutionIntent.INQUIRE,
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
        signal: StoryIntentSignal,
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
        val signal: StoryIntentSignal,
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
                appendLine("Story Mode Replay Validation")
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
        private const val REQUIRED_REPLAY_CASE_COUNT = 60
        private const val WRITE_INTENT_PRECISION_MIN = 0.95
        private const val SELECTOR_PRECISION_MIN = 0.90
        private const val SELECTOR_RECALL_MIN = 0.95
        private const val WRONG_WRITE_RATE_MAX = 0.06

        private const val CLASSIFIER_INPUT_OVERHEAD_TOKENS = 70
        private const val COST_WEIGHT_INPUT = 1.0
        private const val COST_WEIGHT_OUTPUT = 2.0
    }
}
