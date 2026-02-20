package com.ead.dispatch.sample.domain.agents.chat_agent.eval

import com.ead.dispatch.sample.domain.agents.chat_agent.ChatDecisionContext

internal enum class ExpectedChatBehavior {
    INQUIRE,
    EXECUTE,
    SELECTOR,
}

internal data class ChatEvalCase(
    val id: String,
    val prompt: String,
    val expectedBehavior: ExpectedChatBehavior,
    val fromDecisionPrompt: Boolean = false,
    val decisionContext: ChatDecisionContext? = null,
    val seedProfile: ChatEvalSeedProfile = ChatEvalSeedProfile.BASIC,
    val warmupPrompts: List<String> = emptyList(),
)

internal enum class ChatEvalSeedProfile {
    BASIC,
    RICH_CONTEXT,
}

internal fun leanChatEvalCases(): List<ChatEvalCase> = listOf(
    ChatEvalCase(
        id = "inq-1",
        prompt = "Can you create characters in this mode?",
        expectedBehavior = ExpectedChatBehavior.INQUIRE,
    ),
    ChatEvalCase(
        id = "inq-2",
        prompt = "Are you able to update an existing character?",
        expectedBehavior = ExpectedChatBehavior.INQUIRE,
    ),
    ChatEvalCase(
        id = "exec-1",
        prompt = "Create a new character named Orion Vale and save it.",
        expectedBehavior = ExpectedChatBehavior.EXECUTE,
    ),
    ChatEvalCase(
        id = "exec-2",
        prompt = "Add a location named Ember Port and save it now.",
        expectedBehavior = ExpectedChatBehavior.EXECUTE,
    ),
    ChatEvalCase(
        id = "exec-3",
        prompt = "Use option B and create a protagonist named Dark. Save it now.",
        expectedBehavior = ExpectedChatBehavior.EXECUTE,
        fromDecisionPrompt = true,
    ),
    ChatEvalCase(
        id = "sel-1",
        prompt = "Create a new protagonist and choose the best role direction for the main story conflict before saving.",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
    ),
    ChatEvalCase(
        id = "sel-2",
        prompt = "Add a new core location and decide the tone direction that best fits the story arc before saving.",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
    ),
    ChatEvalCase(
        id = "sel-3",
        prompt = "Create a new world rule and choose the most coherent narrative direction before saving.",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
    ),
    ChatEvalCase(
        id = "rich-inq-1",
        prompt = "Can you propose options for a new protagonist without saving yet?",
        expectedBehavior = ExpectedChatBehavior.INQUIRE,
        seedProfile = ChatEvalSeedProfile.RICH_CONTEXT,
        warmupPrompts = listOf(
            "Summarize the current cast tension briefly.",
        ),
    ),
    ChatEvalCase(
        id = "rich-exec-1",
        prompt = "Create and save a supporting character named Nyra who complements Dark's cautious style.",
        expectedBehavior = ExpectedChatBehavior.EXECUTE,
        seedProfile = ChatEvalSeedProfile.RICH_CONTEXT,
    ),
    ChatEvalCase(
        id = "rich-sel-1",
        prompt = "Create and save a new protagonist, but first choose the best narrative direction to fit the existing cast conflict.",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
        seedProfile = ChatEvalSeedProfile.RICH_CONTEXT,
    ),
)

internal data class ChatEvalObservation(
    val case: ChatEvalCase,
    val assistantText: String,
    val toolCalls: List<String>,
    val usedSelector: Boolean,
    val wroteState: Boolean,
    val wordCount: Int,
    val preferenceSaveExecuted: Boolean? = null,
    val preferenceSaveSkippedReason: String? = null,
)

internal fun countWords(text: String): Int {
    if (text.isBlank()) return 0
    return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}
