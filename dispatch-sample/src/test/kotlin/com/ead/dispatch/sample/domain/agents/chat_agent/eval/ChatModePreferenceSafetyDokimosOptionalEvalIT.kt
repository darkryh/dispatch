package com.ead.dispatch.sample.domain.agents.chat_agent.eval

import com.ead.dispatch.sample.domain.agents.chat_agent.ChatDecisionContext
import dev.dokimos.kotlin.dsl.experiment
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CHAT_AGENT_TEST", matches = "(?i)true|1|yes")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ChatModePreferenceSafetyDokimosOptionalEvalIT {

    @Test
    fun `optional dokimos chat preference safety gates`() {
        val cases = listOf(
            ChatEvalCase(
                id = "pref-safe-1",
                prompt = "I usually like melancholic tones and reflective pacing.",
                expectedBehavior = ExpectedChatBehavior.INQUIRE,
                fromDecisionPrompt = false,
            ),
            ChatEvalCase(
                id = "pref-safe-2",
                prompt = "I choose option A.",
                expectedBehavior = ExpectedChatBehavior.INQUIRE,
                fromDecisionPrompt = true,
                decisionContext = ChatDecisionContext(
                    promptId = "selector-pref-1",
                    question = "Pick one direction",
                    optionLabels = listOf("A", "B"),
                    selectedValue = "A",
                    isCustomSelection = false,
                ),
            ),
            ChatEvalCase(
                id = "pref-safe-3",
                prompt = "Proceed with that.",
                expectedBehavior = ExpectedChatBehavior.INQUIRE,
                fromDecisionPrompt = true,
                decisionContext = null,
            ),
        )

        val observations = ChatModeEvalHarness().use { harness ->
            harness.run(cases)
        }

        val byId = observations.associateBy { it.case.id }

        assertEquals(false, byId.getValue("pref-safe-1").preferenceSaveExecuted ?: false)
        assertEquals(
            "chat_non_selector_preference_save_disabled",
            byId.getValue("pref-safe-1").preferenceSaveSkippedReason.orEmpty(),
        )

        assertEquals(false, byId.getValue("pref-safe-2").preferenceSaveExecuted ?: false)
        assertEquals(
            "classifier_not_recommended",
            byId.getValue("pref-safe-2").preferenceSaveSkippedReason.orEmpty(),
        )

        assertEquals(false, byId.getValue("pref-safe-3").preferenceSaveExecuted ?: false)
        assertEquals(
            "classifier_not_recommended",
            byId.getValue("pref-safe-3").preferenceSaveSkippedReason.orEmpty(),
        )

        val result = experiment {
            name = "Dispatch Chat Preference Safety Optional Eval"

            dataset {
                name = "dispatch-chat-preference-safety"
                observations.forEach { observation ->
                    example {
                        input = observation.case.id
                        expected = expectedSnapshot(observation.case.id)
                    }
                }
            }

            task { example ->
                val observation = byId.getValue(example.input())
                mapOf("output" to actualSnapshot(observation))
            }

            evaluators {
                exactMatch { threshold = 1.0 }
            }
        }.run()

        assertTrue(result.passRate() >= 1.0, "Preference safety exact-match gate failed.")
    }

    private fun expectedSnapshot(caseId: String): String = when (caseId) {
        "pref-safe-1" -> "executed=false|reason=chat_non_selector_preference_save_disabled"
        "pref-safe-2" -> "executed=false|reason=classifier_not_recommended"
        "pref-safe-3" -> "executed=false|reason=classifier_not_recommended"
        else -> error("Unknown case id: $caseId")
    }

    private fun actualSnapshot(observation: ChatEvalObservation): String =
        "executed=${observation.preferenceSaveExecuted ?: false}|reason=${observation.preferenceSaveSkippedReason.orEmpty()}"
}
