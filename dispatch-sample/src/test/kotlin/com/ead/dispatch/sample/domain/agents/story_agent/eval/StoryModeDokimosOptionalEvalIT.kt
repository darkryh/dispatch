package com.ead.dispatch.sample.domain.agents.story_agent.eval

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.llm.LLModel
import com.ead.dispatch.sample.domain.AIProvider
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.ExperimentResult
import dev.dokimos.koog.asJudge
import dev.dokimos.kotlin.dsl.experiment
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "STORY_AGENT_TEST", matches = "(?i)true|1|yes")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoryModeDokimosOptionalEvalIT {

    private val originalStoryMainModel: LLModel = AIProvider.Story.main

    @AfterAll
    fun restoreModels() {
        AIProvider.Story.main = originalStoryMainModel
    }

    @Test
    fun `optional dokimos story lane passes deterministic and judge gates`() {
        AIProvider.Story.main = parseStoryModel(
            value = System.getenv("STORY_AGENT_MODEL"),
            default = AIProvider.Story.main,
        )

        val cases = storyModeLeanEvalCases().let { allCases ->
            val maxCases = parseMaxCases(allCases.size)
            allCases.take(maxCases)
        }

        val observations = StoryModeEvalHarness(
            perCaseTimeoutMillis = resolveCaseTimeoutSeconds() * 1000L,
        ).use { harness ->
            harness.run(cases)
        }

        assertEquals(observations.size, cases.size, "Missing observations for one or more story cases.")
        assertDeterministicGates(observations)

        val result = runDokimosJudgeEval(observations)

        println(renderReport(observations, result.passRate(), result.averageScore("Story Intent Quality")))

        assertTrue(
            result.passRate() >= PASS_RATE_MIN,
            "Story Dokimos pass rate gate failed: actual=${format(result.passRate())} expected>=$PASS_RATE_MIN",
        )
        assertTrue(
            result.averageScore("Story Intent Quality") >= INTENT_QUALITY_MIN,
            "Story intent quality gate failed: actual=${format(result.averageScore("Story Intent Quality"))} expected>=$INTENT_QUALITY_MIN",
        )
    }

    private fun runDokimosJudgeEval(observations: List<StoryEvalObservation>): ExperimentResult {
        val judgeProvider = System.getenv("JUDGE_PROVIDER")
            ?.trim()
            ?.lowercase()
            .orEmpty()
            .ifBlank { "deepseek" }

        val judgeModel = when (judgeProvider) {
            "deepseek" -> parseDeepSeekJudgeModel(
                value = System.getenv("JUDGE_MODEL"),
                default = DeepSeekModels.DeepSeekChat,
            )
            else -> parseStoryModel(
                value = System.getenv("JUDGE_MODEL"),
                default = AIProvider.chatGptMini,
            )
        }

        fun judgeAgent() = AIAgent(
            promptExecutor = AIProvider.Sync.executor,
            llmModel = judgeModel,
            maxIterations = 8,
        )

        val judge = asJudge(::judgeAgent)
        val byPrompt = observations.associateBy { it.case.prompt }

        return experiment {
            name = "Dispatch Story Optional Dokimos Eval"
            parallelism = resolveDokimosParallelism()

            dataset {
                name = "dispatch-story-intent-structure"
                observations.forEach { observation ->
                    example {
                        input = observation.case.prompt
                        expected = observation.case.expectedBehavior.name
                    }
                }
            }

            task { example ->
                val observation = byPrompt.getValue(example.input())
                val behaviorSnapshot = buildString {
                    appendLine("assistant_text: ${observation.assistantText.ifBlank { "(empty)" }}")
                    appendLine("used_selector: ${observation.usedSelector}")
                    appendLine("decision_path: ${observation.decisionPath ?: "(unknown)"}")
                    appendLine("volume_delta: ${observation.volumeDelta}")
                    appendLine("chapter_delta: ${observation.chapterDelta}")
                    appendLine("scene_delta: ${observation.sceneDelta}")
                    appendLine("draft_changed: ${observation.draftChanged}")
                    appendLine("write_tool_calls: ${observation.writeToolCalls ?: -1}")
                    appendLine("mutation_total: ${observation.mutationTotal}")
                    appendLine("tool_calls: ${observation.toolCalls.joinToString(",").ifBlank { "(none)" }}")
                }.trim()
                mapOf("output" to behaviorSnapshot)
            }

            evaluators {
                llmJudge(judge) {
                    name = "Story Intent Quality"
                    criteria = """
                        Grade story-mode behavior.
                        Expected label is one of: INQUIRE, EXECUTE, SELECTOR, FOLLOW_UP.

                        Signals:
                        - used_selector
                        - decision_path
                        - structure deltas (volume/chapter/scene)
                        - draft_changed
                        - mutation_total
                        - tool calls and assistant_text

                        Rubric:
                        1) INQUIRE
                        - No persistence changes: structure deltas = 0, draft_changed=false, mutation_total=0.
                        - No selector unless explicitly asking for a choice.

                        2) EXECUTE
                        - Should produce write behavior: structure change and/or draft change and/or mutations.
                        - If user asks to start chapter with empty structure, bootstrap (create missing volume/chapter) is valid.
                        - Assistant should indicate execution happened.

                        3) SELECTOR
                        - Selector-first behavior: used_selector=true.
                        - No writes before user selection: no structure/draft mutation yet.

                        4) FOLLOW_UP
                        - Should avoid writes and selector.
                        - Should ask/indicate need for clarification.

                        Scoring:
                        - 1.0 fully aligned
                        - 0.7-0.9 mostly aligned
                        - 0.4-0.6 mixed
                        - 0.0-0.3 wrong class or contradictory signals
                    """.trimIndent()
                    params(
                        EvalTestCaseParam.INPUT,
                        EvalTestCaseParam.EXPECTED_OUTPUT,
                        EvalTestCaseParam.ACTUAL_OUTPUT,
                    )
                    threshold = INTENT_QUALITY_MIN
                }
            }
        }.run()
    }

    private fun assertDeterministicGates(observations: List<StoryEvalObservation>) {
        val failures = mutableListOf<String>()

        observations.forEach { observation ->
            val expected = observation.case.expectedBehavior
            val hasNonDecisionToolCall = observation.toolCalls.any { !it.equals("requestUserChoice", ignoreCase = true) }
            if (hasNonDecisionToolCall && observation.assistantText.isBlank()) {
                failures += "${observation.case.id}: non-selector tool calls ended with empty assistant response."
            }
            when (expected) {
                ExpectedStoryBehavior.INQUIRE -> {
                    if (observation.wroteState) {
                        failures += "${observation.case.id}: INQUIRE wrote state unexpectedly."
                    }
                    if (observation.usedSelector) {
                        failures += "${observation.case.id}: INQUIRE used selector unexpectedly."
                    }
                }

                ExpectedStoryBehavior.SELECTOR -> {
                    if (!observation.usedSelector) {
                        failures += "${observation.case.id}: SELECTOR did not trigger requestUserChoice."
                    }
                    if (observation.wroteState) {
                        failures += "${observation.case.id}: SELECTOR wrote state before user choice."
                    }
                }

                ExpectedStoryBehavior.EXECUTE -> {
                    val executed = observation.wroteState || ((observation.writeToolCalls ?: 0) > 0)
                    if (!executed) {
                        failures += "${observation.case.id}: EXECUTE did not show write behavior."
                    }
                    if (observation.case.requiresBootstrapCreation) {
                        if (observation.volumeDelta <= 0 || observation.chapterDelta <= 0) {
                            failures += "${observation.case.id}: bootstrap expected volume+chapter creation from empty structure."
                        }
                    }
                }

                ExpectedStoryBehavior.FOLLOW_UP -> {
                    if (observation.wroteState) {
                        failures += "${observation.case.id}: FOLLOW_UP wrote state unexpectedly."
                    }
                    if (observation.usedSelector) {
                        failures += "${observation.case.id}: FOLLOW_UP used selector unexpectedly."
                    }
                }
            }
        }

        assertTrue(
            failures.isEmpty(),
            "Story deterministic gates failed:\n${failures.joinToString("\n")}",
        )
        assertTrue(observations.isNotEmpty(), "Story observations must not be empty.")
    }

    private fun renderReport(
        observations: List<StoryEvalObservation>,
        passRate: Double,
        intentScore: Double,
    ): String = buildString {
        appendLine("Optional Dokimos Story Eval Report")
        appendLine("- cases: ${observations.size}")
        appendLine("- pass_rate: ${format(passRate)}")
        appendLine("- avg_story_intent_quality: ${format(intentScore)}")
        appendLine("- sample_observations:")
        observations.take(10).forEach { observation ->
            appendLine(
                "  * ${observation.case.id}: expected=${observation.case.expectedBehavior.name}, wrote=${observation.wroteState}, selector=${observation.usedSelector}, volΔ=${observation.volumeDelta}, chΔ=${observation.chapterDelta}, sceneΔ=${observation.sceneDelta}",
            )
        }
    }

    private fun parseMaxCases(defaultValue: Int): Int {
        val raw = System.getenv("MAX_CASES")?.trim().orEmpty()
        if (raw.isEmpty()) return defaultValue
        return raw.toIntOrNull()?.coerceIn(1, defaultValue) ?: defaultValue
    }

    private fun resolveDokimosParallelism(): Int {
        val raw = System.getenv("TEST_PARALLELISM")?.trim().orEmpty()
        if (raw.isEmpty()) return DEFAULT_PARALLELISM
        return raw.toIntOrNull()?.coerceIn(1, 16) ?: DEFAULT_PARALLELISM
    }

    private fun resolveCaseTimeoutSeconds(): Long {
        val raw = System.getenv("CASE_TIMEOUT_SECONDS")?.trim().orEmpty()
        if (raw.isEmpty()) return DEFAULT_CASE_TIMEOUT_SECONDS
        return raw.toLongOrNull()?.coerceIn(30L, 600L) ?: DEFAULT_CASE_TIMEOUT_SECONDS
    }

    private fun parseStoryModel(value: String?, default: LLModel): LLModel {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "gpt5nano", "gpt-5-nano" -> AIProvider.chatGptNano
            "gpt5mini", "gpt-5-mini" -> AIProvider.chatGptMini
            "" -> default
            else -> default
        }
    }

    private fun parseDeepSeekJudgeModel(value: String?, default: LLModel): LLModel {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "deepseekchat", "deepseek-chat" -> AIProvider.deepseekChatLlmModel
            "deepseekreasoner", "deepseek-reasoner" -> AIProvider.deepseekReasonerLlmModel
            "" -> default
            else -> default
        }
    }

    private fun format(value: Double): String = "%.3f".format(value)

    private companion object {
        private const val PASS_RATE_MIN = 0.70
        private const val INTENT_QUALITY_MIN = 0.78
        private const val DEFAULT_CASE_TIMEOUT_SECONDS = 120L
        private const val DEFAULT_PARALLELISM = 3
    }
}
