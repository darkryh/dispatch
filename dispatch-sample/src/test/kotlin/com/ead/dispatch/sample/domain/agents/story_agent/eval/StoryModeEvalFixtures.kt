package com.ead.dispatch.sample.domain.agents.story_agent.eval

internal enum class ExpectedStoryBehavior {
    INQUIRE,
    EXECUTE,
    SELECTOR,
    FOLLOW_UP,
}

internal enum class StoryEvalSeedProfile {
    EMPTY_STRUCTURE,
    BASIC_STRUCTURE,
    RICH_STRUCTURE,
}

internal data class StoryEvalCase(
    val id: String,
    val prompt: String,
    val expectedBehavior: ExpectedStoryBehavior,
    val seedProfile: StoryEvalSeedProfile = StoryEvalSeedProfile.BASIC_STRUCTURE,
    val fromDecisionPrompt: Boolean = false,
    val requiresBootstrapCreation: Boolean = false,
    val warmupPrompts: List<String> = emptyList(),
)

internal data class StoryEvalObservation(
    val case: StoryEvalCase,
    val assistantText: String,
    val toolCalls: List<String>,
    val usedSelector: Boolean,
    val decisionPath: String?,
    val volumeDelta: Int,
    val chapterDelta: Int,
    val sceneDelta: Int,
    val draftChanged: Boolean,
    val writeToolCalls: Int?,
    val mutationCreateCount: Int?,
    val mutationUpdateCount: Int?,
    val mutationDeleteCount: Int?,
) {
    val mutationTotal: Int
        get() = (mutationCreateCount ?: 0) + (mutationUpdateCount ?: 0) + (mutationDeleteCount ?: 0)

    val structureChanged: Boolean
        get() = volumeDelta > 0 || chapterDelta > 0 || sceneDelta > 0

    val wroteState: Boolean
        get() = structureChanged || draftChanged || mutationTotal > 0
}

internal fun storyModeLeanEvalCases(): List<StoryEvalCase> = listOf(
    StoryEvalCase(
        id = "inq-1",
        prompt = "Can you create a first chapter in this mode?",
        expectedBehavior = ExpectedStoryBehavior.INQUIRE,
        seedProfile = StoryEvalSeedProfile.EMPTY_STRUCTURE,
    ),
    StoryEvalCase(
        id = "exec-bootstrap-1",
        prompt = "Let's start chapter 1 now with a tense opening in a ruined port city.",
        expectedBehavior = ExpectedStoryBehavior.EXECUTE,
        seedProfile = StoryEvalSeedProfile.EMPTY_STRUCTURE,
        requiresBootstrapCreation = true,
    ),
    StoryEvalCase(
        id = "exec-progressive-1",
        prompt = "Update chapter 1 draft now to add a sharper conflict in the first scene.",
        expectedBehavior = ExpectedStoryBehavior.EXECUTE,
        seedProfile = StoryEvalSeedProfile.BASIC_STRUCTURE,
    ),
    StoryEvalCase(
        id = "exec-oneshot-1",
        prompt = "Write and apply chapter 1 now in one full pass. Keep it concise and coherent.",
        expectedBehavior = ExpectedStoryBehavior.EXECUTE,
        seedProfile = StoryEvalSeedProfile.BASIC_STRUCTURE,
    ),
    StoryEvalCase(
        id = "sel-creative-1",
        prompt = "Create a new protagonist now and decide the best narrative direction for the current core conflict before saving.",
        expectedBehavior = ExpectedStoryBehavior.SELECTOR,
        seedProfile = StoryEvalSeedProfile.RICH_STRUCTURE,
    ),
    StoryEvalCase(
        id = "sel-creative-2",
        prompt = "Add the next chapter direction now, but choose the tone/style path that best fits current continuity before applying.",
        expectedBehavior = ExpectedStoryBehavior.SELECTOR,
        seedProfile = StoryEvalSeedProfile.RICH_STRUCTURE,
    ),
    StoryEvalCase(
        id = "exec-bounded-1",
        prompt = "Update chapter 1 summary now: first person, past tense, melancholic tone, exactly one paragraph.",
        expectedBehavior = ExpectedStoryBehavior.EXECUTE,
        seedProfile = StoryEvalSeedProfile.RICH_STRUCTURE,
    ),
    StoryEvalCase(
        id = "sel-destructive-1",
        prompt = "Delete chapter 1 now.",
        expectedBehavior = ExpectedStoryBehavior.SELECTOR,
        seedProfile = StoryEvalSeedProfile.BASIC_STRUCTURE,
    ),
    StoryEvalCase(
        id = "exec-decision-1",
        prompt = "Use option 2 and apply it now.",
        expectedBehavior = ExpectedStoryBehavior.EXECUTE,
        fromDecisionPrompt = true,
        seedProfile = StoryEvalSeedProfile.RICH_STRUCTURE,
    ),
    StoryEvalCase(
        id = "follow-up-1",
        prompt = "Do it.",
        expectedBehavior = ExpectedStoryBehavior.FOLLOW_UP,
        seedProfile = StoryEvalSeedProfile.BASIC_STRUCTURE,
    ),
    StoryEvalCase(
        id = "exec-multi-tool-1",
        prompt = "Create volume 1, chapter 1, and scene 1 now, then apply a short chapter draft.",
        expectedBehavior = ExpectedStoryBehavior.EXECUTE,
        seedProfile = StoryEvalSeedProfile.EMPTY_STRUCTURE,
        requiresBootstrapCreation = true,
    ),
    StoryEvalCase(
        id = "rich-sel-2",
        prompt = "Set the next chapter direction now, but choose the best tone and conflict path for continuity before applying the draft.",
        expectedBehavior = ExpectedStoryBehavior.SELECTOR,
        seedProfile = StoryEvalSeedProfile.RICH_STRUCTURE,
        warmupPrompts = listOf(
            "Update chapter 1 summary now to emphasize Dark's hesitation and Mira's distrust.",
            "Update chapter 1 summary now to add a failed rescue and visible alliance tension.",
            "Create and save an event called Dockside Fallout tied to the failed rescue.",
            "Create and save an arc named Fractured Trust focused on alliance instability.",
            "Create and save a timeline entry called Rumor Spread where manipulated route logs start circulating.",
            "Create and save an organization called Quiet Ledger that benefits from route instability.",
        ),
    ),
    StoryEvalCase(
        id = "rich-sel-3",
        prompt = "Create and apply a new protagonist arc pivot now, but decide the best narrative role and voice direction first before committing changes.",
        expectedBehavior = ExpectedStoryBehavior.SELECTOR,
        seedProfile = StoryEvalSeedProfile.RICH_STRUCTURE,
        warmupPrompts = listOf(
            "Create and save an arc called Harbor Schism where Dark and Mira split over map ethics.",
            "Create and save an event called Harbor Negotiation where rival factions force a tense truce.",
            "Create and save an event called Conflicting Testimonies where witnesses contradict each other.",
            "Create and save a world rule called Emotional Echo where unresolved guilt distorts route memory.",
            "Create and save an event named Echo Surge where map routes rewrite themselves overnight.",
            "Create and save an arc called Signal Schism about rival interpretations of map truth.",
            "Create and save a timeline entry called Moral Breakpoint centered on unreliable memory and fractured trust.",
            "Create and save a timeline entry named Chapter 2 Fracture Point.",
            "Create and save a culture called Drift Monastics that values emotional restraint in navigation.",
        ),
    ),
)
