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
    val injectedHistoryProfile: ChatEvalInjectedHistoryProfile? = null,
    val warmupPrompts: List<String> = emptyList(),
)

internal enum class ChatEvalSeedProfile {
    BASIC,
    RICH_CONTEXT,
}

internal enum class ChatEvalInjectedHistoryProfile {
    LIGHT,
    HEAVY,
    IMPORTANT_FIT_LIGHT_REGRESSION,
    IMPORTANT_FIT_HEAVY_REGRESSION,
    PROTAGONIST_BEST_FIT_REGRESSION,
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
        id = "implicit-important-fit-light-regression",
        prompt = "okay, can you create the stabilizer artifact and you can decide what's the best fit for it?",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
        injectedHistoryProfile = ChatEvalInjectedHistoryProfile.IMPORTANT_FIT_LIGHT_REGRESSION,
    ),
    ChatEvalCase(
        id = "implicit-important-fit-light-control",
        prompt = "Create and save a small dock token item traders use to mark paid storage.",
        expectedBehavior = ExpectedChatBehavior.EXECUTE,
        injectedHistoryProfile = ChatEvalInjectedHistoryProfile.IMPORTANT_FIT_LIGHT_REGRESSION,
    ),
    ChatEvalCase(
        id = "implicit-important-fit-heavy-regression",
        prompt = "okay, can you create the anchoring artifact and you can decide what's the best fit for it?",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
        seedProfile = ChatEvalSeedProfile.RICH_CONTEXT,
        injectedHistoryProfile = ChatEvalInjectedHistoryProfile.IMPORTANT_FIT_HEAVY_REGRESSION,
    ),
    ChatEvalCase(
        id = "implicit-important-fit-heavy-control",
        prompt = "Create and save a small maintenance tool artifact for relay workers to carry in one scene.",
        expectedBehavior = ExpectedChatBehavior.EXECUTE,
        seedProfile = ChatEvalSeedProfile.RICH_CONTEXT,
        injectedHistoryProfile = ChatEvalInjectedHistoryProfile.IMPORTANT_FIT_HEAVY_REGRESSION,
    ),
    ChatEvalCase(
        id = "implicit-protagonist-best-fit-regression",
        prompt = "okay, can you create another protagonist and you can decide what's the best fit for it?",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
        injectedHistoryProfile = ChatEvalInjectedHistoryProfile.PROTAGONIST_BEST_FIT_REGRESSION,
    ),
    ChatEvalCase(
        id = "implicit-light-sel-1",
        prompt = "Add the person who would take point if Dark and Mira stop trusting each other.",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
        injectedHistoryProfile = ChatEvalInjectedHistoryProfile.LIGHT,
    ),
    ChatEvalCase(
        id = "implicit-light-control-1",
        prompt = "Add a dockside vendor who sells route ink and rumors.",
        expectedBehavior = ExpectedChatBehavior.EXECUTE,
        injectedHistoryProfile = ChatEvalInjectedHistoryProfile.LIGHT,
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
        id = "rich-exec-2",
        prompt = "Create and save two new characters, one new location, and one new event for the current story context.",
        expectedBehavior = ExpectedChatBehavior.EXECUTE,
        seedProfile = ChatEvalSeedProfile.RICH_CONTEXT,
    ),
    ChatEvalCase(
        id = "rich-sel-3",
        prompt = "Create and save a new protagonist for this story line, but choose the best narrative role and tone direction first before saving so continuity stays coherent.",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
        seedProfile = ChatEvalSeedProfile.RICH_CONTEXT,
        warmupPrompts = listOf(
            "Create and save a character named Tal who distrusts institutions and pressures Dark to act faster.",
            "Create and save an event where Mira discovers forged route logs tied to a hidden faction.",
            "Create and save a location named Echo Relay where memories leak into active navigation maps.",
            "Create and save an organization called The Quiet Ledger that profits from route instability.",
            "Create and save a culture called Drift Monastics focused on emotional restraint in navigation.",
            "Create and save an arc named Signal Schism about rival interpretations of the living map.",
            "Create and save a world rule that map fragments amplify unresolved guilt into false routes.",
            "Update the main story plot outline to emphasize trust fractures and moral ambiguity in alliances.",
            "Create and save a timeline entry called Chapter Pivot where alliances split over map ethics.",
        ),
    ),
    ChatEvalCase(
        id = "implicit-heavy-sel-1",
        prompt = "Create the faction behind the forged route logs that has been steering recent conflicts.",
        expectedBehavior = ExpectedChatBehavior.SELECTOR,
        seedProfile = ChatEvalSeedProfile.RICH_CONTEXT,
        injectedHistoryProfile = ChatEvalInjectedHistoryProfile.HEAVY,
    ),
    ChatEvalCase(
        id = "implicit-heavy-control-1",
        prompt = "Create a one-scene scout who helps carry supplies through Rook's Salvage Tavern.",
        expectedBehavior = ExpectedChatBehavior.EXECUTE,
        seedProfile = ChatEvalSeedProfile.RICH_CONTEXT,
        injectedHistoryProfile = ChatEvalInjectedHistoryProfile.HEAVY,
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
