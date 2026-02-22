package com.ead.dispatch.sample.domain.agents.chat_agent.eval

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.llm.LLModel
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.isDecisionToolName
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.isWriteToolName
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.ExperimentResult
import dev.dokimos.koog.asJudge
import dev.dokimos.kotlin.dsl.experiment
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CHAT_AGENT_TEST", matches = "(?i)true|1|yes")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatModeDokimosOptionalEvalIT {

    private val originalChatMainModel: LLModel = AIProvider.Chat.main

    @AfterAll
    fun restoreModels() {
        AIProvider.Chat.main = originalChatMainModel
    }

    @Test
    fun `optional dokimos chat lane passes deterministic and judge gates`() {
        assertTimeoutPreemptively(Duration.ofSeconds(resolveTimeoutSeconds())) {
            AIProvider.Chat.main = parseChatModel(
                value = System.getenv("CHAT_AGENT_MODEL"),
                default = AIProvider.Chat.main,
            )

            val cases = leanChatEvalCases().let { allCases ->
                val maxCases = parseMaxCases(allCases.size)
                allCases.take(maxCases)
            }

            val observations = ChatModeEvalHarness().use { harness ->
                harness.run(cases)
            }

            assertEquals(observations.size, cases.size, "Missing observations for one or more cases.")

            assertDeterministicGates(observations)

            val result = runDokimosJudgeEval(observations)

            println(renderReport(observations, result.passRate(), result.averageScore("Intent Appropriateness")))

            assertTrue(
                result.passRate() >= PASS_RATE_MIN,
                "Dokimos pass rate gate failed: actual=${format(result.passRate())} expected>=$PASS_RATE_MIN",
            )
            assertTrue(
                result.averageScore("Intent Appropriateness") >= INTENT_QUALITY_MIN,
                "Intent quality gate failed: actual=${format(result.averageScore("Intent Appropriateness"))} expected>=$INTENT_QUALITY_MIN",
            )
        }
    }

    private fun runDokimosJudgeEval(observations: List<ChatEvalObservation>): ExperimentResult {
        val judgeProvider = System.getenv("JUDGE_PROVIDER")
            ?.trim()
            ?.lowercase()
            .orEmpty()
            .ifBlank { "deepseek" }

        val judgeModel= when (judgeProvider) {
            "deepseek" -> {
                parseDeepSeekJudgeModel(
                    value = System.getenv("JUDGE_MODEL"),
                    default = DeepSeekModels.DeepSeekChat,
                )
            }
            else -> {
                parseChatModel(
                    value = System.getenv("JUDGE_MODEL"),
                    default = AIProvider.chatGptMini,
                )
            }
        }

        fun judgeAgent() = AIAgent(
            promptExecutor = AIProvider.Sync.executor,
            llmModel = judgeModel,
            maxIterations = 8,
        )

        val judge = asJudge(::judgeAgent)
        val byPrompt = observations.associateBy { it.case.prompt }

        return experiment {
            name = "Dispatch Chat Optional Dokimos Eval"
            parallelism = resolveDokimosParallelism()

            dataset {
                name = "dispatch-chat-intent-brevity"
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
                    appendLine("wrote_state: ${observation.wroteState}")
                    appendLine("tool_calls: ${observation.toolCalls.joinToString(",").ifBlank { "(none)" }}")
                }.trim()
                mapOf("output" to behaviorSnapshot)
            }

            evaluators {
                llmJudge(judge) {
                    name = "Intent Appropriateness"
                    criteria = """
                        You are grading chat-mode behavior quality for a story assistant.
                        Expected label is one of: INQUIRE, EXECUTE, SELECTOR.

                        Inputs available:
                        - assistant_text: user-facing response text
                        - used_selector: whether requestUserChoice was called
                        - wrote_state: whether persistent story state changed
                        - tool_calls: tools invoked this turn

                        Decision rubric:
                        1) INQUIRE behavior (informational turn)
                        - Must NOT mutate state: wrote_state=false
                        - Must NOT trigger selector: used_selector=false
                        - assistant_text should be informational/capability/advice language, not execution completion.

                        2) EXECUTE behavior (apply now turn)
                        - Must mutate state: wrote_state=true
                        - Should not be blocked by selector unless the prompt explicitly requires a prior choice.
                        - assistant_text should clearly indicate action execution/completion.

                        3) SELECTOR behavior (high-impact delegated creative decision)
                        - Must trigger selector first: used_selector=true
                        - Must NOT mutate state before user choice: wrote_state=false
                        - tool_calls should reflect selector-first flow.
                        - assistant_text may be brief or empty; do not penalize brevity if selector signals are correct.

                        Scoring:
                        - 1.0: behavior fully matches expected label and no contradictory signals.
                        - 0.7-0.9: mostly correct with minor wording/clarity weakness.
                        - 0.4-0.6: mixed signals or partial mismatch.
                        - 0.0-0.3: wrong behavior class (e.g., executes on INQUIRE or writes before selector resolution).
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

    private fun assertDeterministicGates(observations: List<ChatEvalObservation>) {
        val failures = mutableListOf<String>()

        observations.forEach { observation ->
            val expected = observation.case.expectedBehavior
            val hasNonDecisionToolCall = observation.toolCalls.any { !isDecisionToolName(it) }
            val hasWriteToolCall = observation.toolCalls.any { tool ->
                !isDecisionToolName(tool) && isWriteToolName(tool)
            }
            if (hasNonDecisionToolCall && observation.assistantText.isBlank()) {
                failures += "${observation.case.id}: non-selector tool calls ended with empty assistant response."
            }
            when (expected) {
                ExpectedChatBehavior.INQUIRE -> {
                    if (observation.wroteState) {
                        failures += "${observation.case.id}: INQUIRE wrote state unexpectedly. tools=${observation.toolCalls}"
                    }
                    if (observation.usedSelector) {
                        failures += "${observation.case.id}: INQUIRE used selector unexpectedly."
                    }
                }

                ExpectedChatBehavior.SELECTOR -> {
                    if (!observation.usedSelector) {
                        failures += "${observation.case.id}: SELECTOR did not trigger requestUserChoice. tools=${observation.toolCalls}"
                    }
                    if (hasWriteToolCall) {
                        failures += "${observation.case.id}: SELECTOR called write tools before user choice. tools=${observation.toolCalls}"
                    }
                    if (observation.wroteState) {
                        failures += "${observation.case.id}: SELECTOR mutated state before user choice."
                    }
                }

                ExpectedChatBehavior.EXECUTE -> {
                    if (!observation.wroteState) {
                        failures += "${observation.case.id}: EXECUTE did not mutate state. tools=${observation.toolCalls}"
                    }
                }
            }
        }

        assertTrue(
            failures.isEmpty(),
            "Deterministic gates failed:\n${failures.joinToString("\n")}",
        )

        assertFalse(observations.isEmpty(), "Observation list must not be empty.")
    }

    private fun renderReport(
        observations: List<ChatEvalObservation>,
        passRate: Double,
        intentScore: Double,
    ): String = buildString {
        appendLine("Optional Dokimos Chat Eval Report")
        appendLine("- cases: ${observations.size}")
        appendLine("- pass_rate: ${format(passRate)}")
        appendLine("- avg_intent_appropriateness: ${format(intentScore)}")
        appendLine("- sample_observations:")
        observations.take(8).forEach { observation ->
            appendLine(
                "  * ${observation.case.id}: expected=${observation.case.expectedBehavior.name}, wrote=${observation.wroteState}, selector=${observation.usedSelector}, words=${observation.wordCount}, path=${observation.decisionPath ?: "?"}, exec=${observation.executionIntent ?: "?"}, action=${observation.resolvedAction ?: "?"}, creative=${observation.requiresCreativeChoice?.toString() ?: "?"}, beforePersist=${observation.decisionBeforePersist?.toString() ?: "?"}",
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

    private fun resolveTimeoutSeconds(): Long {
        val raw = System.getenv("TEST_TIMEOUT_SECONDS")?.trim().orEmpty()
        if (raw.isEmpty()) return DEFAULT_TIMEOUT_SECONDS
        return raw.toLongOrNull()?.coerceIn(60L, 600L) ?: DEFAULT_TIMEOUT_SECONDS
    }

    private fun parseChatModel(value: String?, default: LLModel): LLModel {
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
        private const val PASS_RATE_MIN = 0.75
        private const val INTENT_QUALITY_MIN = 0.80
        private const val DEFAULT_TIMEOUT_SECONDS = 300L
        private const val DEFAULT_PARALLELISM = 4
    }
}
