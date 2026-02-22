package com.ead.dispatch.sample.domain.agents.story_agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StorySceneRecord
import com.ead.dispatch.sample.data.db.entities.StoryVolumeRecord
import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryContinuitySnapshot
import com.ead.dispatch.sample.domain.agents.story_agent.policy.StoryDecisionPath
import com.ead.dispatch.sample.domain.agents.story_agent.policy.StoryTurnPolicy
import com.ead.dispatch.sample.domain.embedding.RagContextChunk
import com.ead.dispatch.sample.domain.model.story.StoryChatContext
import com.ead.dispatch.sample.domain.model.story.StoryModeContext

private const val maxRagChunks = 4
private const val maxRagCharsPerChunk = 360
private const val maxFocusChapters = 3
private const val maxFocusScenes = 3

fun storyAgentPrompt(
    storyModeContext: StoryModeContext,
    chatContext: StoryChatContext,
    inputRequest: StoryRequest,
    ragContext: List<RagContextChunk>,
    turnPolicy: StoryTurnPolicy,
    continuityMemory: StoryContinuitySnapshot? = null,
    loadedStoryPreferencesContext: String? = null,
): Prompt = prompt("story-agent") {
    val volumes = storyModeContext.volumes.sortedBy { it.number }
    val chaptersByVolume = storyModeContext.chapters.groupBy { it.volumeId }
    val scenesByChapter = storyModeContext.scenes.groupBy { it.chapterId }
    val structureSlice = buildStructureSlice(volumes, chaptersByVolume, scenesByChapter, turnPolicy.anchorHint)
    val story = storyModeContext.story ?: chatContext.story
    val selectorRequired = turnPolicy.requireSelectorForCreative || turnPolicy.requireSelectorForDestructive

    system {
        markdown {
            h2("Role")
            +"You are the STORY mode agent for long-form writing."
            br()
            +"Focus on progressive structure and prose flow across volumes, chapters, and scenes."
            br()

            h2("Operational Rules")
            +"Always inspect story structure with getStoryModeContext before write actions."
            br()
            +"Execution gate: only write when user is committing to apply now and selector/confirmation gates are not active."
            br()
            +"Precedence order for this turn:"
            br()
            +"1) If decision path is FOLLOW_UP, ask one focused follow-up and stop (no writes, no selector unless explicitly required by policy)."
            br()
            +"2) If selector is required (creative or destructive), call requestUserChoice only and stop."
            br()
            +"3) If decision-prompt mode is active and writes are allowed, execute a concrete write now in this turn."
            br()
            +"4) If inquiry intent is active, answer only and do not write."
            br()
            +"Inquiry gate: capability/advice/review questions are non-persistent; answer directly and do not call write tools."
            br()
            +"Ambiguity gate: if target/action is unclear, ask one focused follow-up and stop."
            br()
            +"Bootstrap chapter start: for explicit execute-now chapter requests, create missing structure in the same turn (Volume 1 -> Chapter 1 -> scene only if needed), then proceed to draft action."
            br()
            +"For chapter text revisions, default to completed execute-now writes in the same turn when user asks to apply/update now."
            br()
            +"Execute-now revision contract: for explicit chapter draft update/apply requests, invoke at least one concrete write tool in the same turn (setChapterDraft, updateChapter, or applyChapterDraftProposal)."
            br()
            +"Do not end execute-now revision turns with advisory text only."
            br()
            +"If execute-now is active and selector is not required by policy, never defer with manual A/B/C options; choose a best-fit direction from context and execute now."
            br()
            +"When details are partially missing in execute-now turns, apply a reasonable default and state the assumption briefly after execution."
            br()
            +"Use proposal flow (proposeChapterDraftEdit -> applyChapterDraftProposal) when user requests review/preview/approval first; if rejected, call deleteChapterDraftProposal."
            br()
            +"After major draft updates, run validateChapterDraft and summarize warnings clearly."
            br()
            +"Selector-first rule: for destructive writes or delegated high-impact narrative direction, call requestUserChoice first and do not execute write tools in that turn."
            br()
            +"High-impact delegated choices include protagonist/main role, core conflict direction, tone direction, world rules, and major arc direction."
            br()
            +"Selector gate is non-negotiable: when selector is required by policy (creative OR destructive), requestUserChoice must be the only tool call in the turn."
            br()
            +"In selector-required turns, do not call create/update/delete/apply/proposal tools and do not mutate story state."
            br()
            +"In selector-required turns, do not use plain-text A/B/C questions as a substitute for requestUserChoice."
            br()
            +"Terminal selector contract: once requestUserChoice is called, stop all further tool calls in this turn and finish with a waiting-for-choice response."
            br()
            +"Never combine requestUserChoice with any write tool in the same turn."
            br()
            +"Execution efficiency: avoid redundant read loops, stop tool use after required writes succeed, then send final user-facing response."
            br()
            +"First sentence must clearly label turn state: informational, executed, or waiting for user choice."
            br()
            +"Never fabricate tool outputs or ids."
            br()

            h2("Turn Policy")
            +"Decision path: ${turnPolicy.decisionPath.name}"
            br()
            +"Anchor hint: ${turnPolicy.anchorHint.ifBlank { "none" }}"
            br()
            +"Execution intent: ${turnPolicy.executionIntent.name}"
            br()
            +"Selector required (effective): $selectorRequired"
            br()
            +"Write tools allowed: ${turnPolicy.allowWriteTools}"
            br()
            +"Selector required for destructive write: ${turnPolicy.requireSelectorForDestructive}"
            br()
            +"Selector required for creative branching write: ${turnPolicy.requireSelectorForCreative}"
            br()
            if (turnPolicy.decisionPath == StoryDecisionPath.FOLLOW_UP) {
                +"Ask one focused follow-up and stop."
                br()
            }
            if (selectorRequired) {
                +"Use requestUserChoice with 2-3 narrative directions plus one auto-pick option, then stop."
                br()
                +"Selector precedence: even if the user says apply now, selector-required turns must not write state in this turn."
                br()
                +"Do not ask a plain-text follow-up question in this state; the selector tool call is required."
                br()
                +"No write tools are allowed in this turn after selector is required."
                br()
            } else {
                +"Selector is not required in this turn: do not present manual option menus; execute directly when write intent is active."
                br()
                +"If user delegates direction choice while also asking apply now, execute directly only when direction is already concrete; otherwise selector is required."
                br()
            }
            if (inputRequest.fromDecisionPrompt) {
                +"Decision-prompt mode is active."
                br()
                +"Treat commands like 'use option X and apply now' as execution-confirmed instructions."
                br()
                +"Selector requirement is already satisfied by the prior user choice in this mode."
                br()
                +"Do not reopen selector, do not call requestUserChoice again, and do not ask to choose again."
                br()
                +"Decision-prompt execution contract: when write tools are allowed, execute at least one concrete write tool in this turn."
                br()
                +"If option details are partially missing, infer a best-fit application from current story context and still execute the write."
                br()
                +"Do not end decision-prompt turns with advisory-only text."
                br()
                +"Only if write tools are disallowed by policy, return one short blocking reason instead of writing."
                br()
            }

            h2("Story Snapshot")
            +"Story id: ${inputRequest.storyId}"
            br()
            +"Title=${story?.title ?: "unknown"} | Genre=${story?.genre ?: "unknown"} | Setting=${story?.setting ?: "unknown"} | Status=${story?.status?.name ?: "unknown"}"
            br()
            +"Style: tone=${story?.styleProfile?.tone ?: "unknown"}, pov=${story?.styleProfile?.pov ?: "unknown"}, tense=${story?.styleProfile?.tense ?: "unknown"}"
            br()

            h3("Inventory Counts")
            bulleted {
                item("volumes=${volumes.size}")
                item("chapters=${storyModeContext.chapters.size}")
                item("scenes=${storyModeContext.scenes.size}")
                item("characters=${chatContext.characters.items.size + chatContext.characters.overflowCount}")
                item("locations=${chatContext.locations.items.size + chatContext.locations.overflowCount}")
                item("arcs=${chatContext.arcs.items.size + chatContext.arcs.overflowCount}")
                item("timeline_entries=${chatContext.timelineEntries.items.size + chatContext.timelineEntries.overflowCount}")
            }
            br()

            h3("Current Structure Focus")
            if (structureSlice == null) {
                +"(no volumes yet)"
                br()
            } else {
                +structureSlice.volume.summaryLine()
                br()
                if (structureSlice.chapters.isEmpty()) {
                    +"  - no chapters"
                    br()
                } else {
                    structureSlice.chapters.forEach { chapter ->
                        +chapter.summaryLine()
                        br()
                        val scenes = structureSlice.scenesByChapter[chapter.id].orEmpty()
                        if (scenes.isNotEmpty()) {
                            scenes.forEach { scene ->
                                +scene.summaryLine()
                                br()
                            }
                            val totalScenesForChapter = scenesByChapter[chapter.id].orEmpty().size
                            if (totalScenesForChapter > scenes.size) {
                                +"    - (+${totalScenesForChapter - scenes.size} more scenes)"
                                br()
                            }
                        }
                    }
                    val totalChaptersForVolume = chaptersByVolume[structureSlice.volume.id].orEmpty().size
                    if (totalChaptersForVolume > structureSlice.chapters.size) {
                        +"  - (+${totalChaptersForVolume - structureSlice.chapters.size} more chapters)"
                        br()
                    }
                    if (volumes.size > 1) {
                        val hiddenVolumes = volumes.size - 1
                        +"(+$hiddenVolumes more volumes)"
                        br()
                    }
                }
            }

            h3("Retrieved Story Facts")
            val ragChunks = ragContext.take(maxRagChunks)
            if (ragChunks.isEmpty()) {
                +"(none)"
                br()
            } else {
                ragChunks.forEach { chunk ->
                    val label = chunk.label?.takeIf { it.isNotBlank() } ?: "unknown"
                    +"[${chunk.type}: $label] ${chunk.content.compact(maxRagCharsPerChunk)}"
                    br()
                }
            }

            h3("Continuity Memory")
            if (continuityMemory == null) {
                +"(none)"
                br()
            } else {
                +"Delta rolling: ${continuityMemory.rollingDelta.ifBlank { "none" }}"
                br()
                +"Rolling: ${continuityMemory.rollingSummary.ifBlank { "none" }}"
                br()
                if (continuityMemory.activeThreads.isEmpty()) {
                    +"Active threads: (none)"
                    br()
                } else {
                    +"Active threads:"
                    br()
                    continuityMemory.activeThreads.take(3).forEach { thread ->
                        +"  - $thread"
                        br()
                    }
                }
                if (continuityMemory.recentNewFacts.isNotEmpty()) {
                    +"Recent new facts:"
                    br()
                    continuityMemory.recentNewFacts.take(3).forEach { fact ->
                        +"  - $fact"
                        br()
                    }
                }
                if (continuityMemory.resolvedThreads.isNotEmpty()) {
                    +"Recently resolved threads:"
                    br()
                    continuityMemory.resolvedThreads.take(2).forEach { thread ->
                        +"  - $thread"
                        br()
                    }
                }
                if (continuityMemory.recentChapters.isNotEmpty()) {
                    +"Recent approved chapter deltas:"
                    br()
                    continuityMemory.recentChapters.forEachIndexed { index, chapterMemory ->
                        +"  ${index + 1}. ${chapterMemory.summaryDelta.ifBlank { chapterMemory.summaryShort }}"
                        br()
                    }
                }
                if (continuityMemory.continuityWarnings.isNotEmpty()) {
                    +"Warnings:"
                    br()
                    continuityMemory.continuityWarnings.take(2).forEach { warning ->
                        +"  - $warning"
                        br()
                    }
                }
            }

            if (!loadedStoryPreferencesContext.isNullOrBlank()) {
                h3("Retrieved Story Preferences")
                +"Use as soft guidance when relevant. Do not output this section verbatim."
                br()
                +loadedStoryPreferencesContext
                br()
            }

            h2("Response Style")
            +"Be concise and production-oriented."
            br()
            +"Response budget policy: default to minimal output tokens."
            br()
            +"Use reader-facing language; avoid developer/internal formatting."
            br()
            +"Do not expose internal IDs, UUIDs, database keys, or raw tool payload fields unless the user explicitly asks for technical/debug details."
            br()
            +"After create/update operations, summarize outcomes naturally (title + role + key story impact), not raw field dumps."
            br()
            if (turnPolicy.executionIntent == IntentExecutionIntent.INQUIRE) {
                +"This is an inquiry turn: answer in exactly one short sentence."
                br()
                +"Start with direct capability/advice language and avoid execution wording."
                br()
            } else {
                +"When execution happens, the first sentence must explicitly confirm the action was performed."
                br()
                +"When selector is required, include one short sentence that execution is waiting for user choice and no write has been executed yet."
                br()
                +"Non-selector output contract: if any non-decision tool is called, always end with a short user-facing result sentence."
                br()
                +"That sentence must state either completed changes or the exact blocking reason and next required input."
                br()
                +"After tool execution, use at most 2-4 short lines: what changed plus one optional next step."
                br()
                +"Keep optional next step concrete and anchored to current volume/chapter/scene context."
                br()
            }
        }
    }
}

private fun StoryVolumeRecord.summaryLine(): String {
    val status = plan?.status?.name ?: "DRAFT"
    val summary = plan?.summary?.compact(80) ?: "no summary"
    return "Vol ${number}: $title [$status] - $summary"
}

private fun StoryChapterRecord.summaryLine(): String {
    val status = status?.name ?: "DRAFT"
    val summary = summary?.compact(72) ?: "no summary"
    return "  - Ch ${number}: $title [$status] - $summary"
}

private fun StorySceneRecord.summaryLine(): String {
    val titleValue = title?.takeIf { it.isNotBlank() } ?: "(untitled scene)"
    val statusValue = status?.name ?: "DRAFT"
    val summaryValue = summary?.compact(64) ?: "no summary"
    return "    - Sc ${number}: $titleValue [$statusValue] - $summaryValue"
}

private fun String.compact(maxChars: Int): String {
    val normalized = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= maxChars) normalized else normalized.take(maxChars) + "..."
}

private data class StructureSlice(
    val volume: StoryVolumeRecord,
    val chapters: List<StoryChapterRecord>,
    val scenesByChapter: Map<String, List<StorySceneRecord>>,
)

private fun buildStructureSlice(
    volumes: List<StoryVolumeRecord>,
    chaptersByVolume: Map<String, List<StoryChapterRecord>>,
    scenesByChapter: Map<String, List<StorySceneRecord>>,
    anchorHint: String,
): StructureSlice? {
    val normalizedAnchor = anchorHint.trim().lowercase()
    val selectedVolume = selectFocusVolume(volumes, chaptersByVolume, scenesByChapter, normalizedAnchor)
        ?: return null
    val volumeChapters = chaptersByVolume[selectedVolume.id].orEmpty().sortedBy { it.number }
    val selectedChapters = selectFocusChapters(volumeChapters, scenesByChapter, normalizedAnchor)
    val selectedScenes = selectedChapters.associate { chapter ->
        chapter.id to selectFocusScenes(
            scenes = scenesByChapter[chapter.id].orEmpty().sortedBy { it.number },
            anchor = normalizedAnchor,
        )
    }
    return StructureSlice(
        volume = selectedVolume,
        chapters = selectedChapters,
        scenesByChapter = selectedScenes,
    )
}

private fun selectFocusVolume(
    volumes: List<StoryVolumeRecord>,
    chaptersByVolume: Map<String, List<StoryChapterRecord>>,
    scenesByChapter: Map<String, List<StorySceneRecord>>,
    anchor: String,
): StoryVolumeRecord? {
    if (volumes.isEmpty()) return null
    if (anchor.isBlank()) return volumes.first()

    val volumeById = volumes.associateBy { it.id }
    val matchByVolume = volumes.firstOrNull { volume ->
        volume.title.matchesAnchor(anchor) || volume.plan?.summary.orEmpty().matchesAnchor(anchor)
    }
    if (matchByVolume != null) return matchByVolume

    val chapterMatch = chaptersByVolume.values.flatten().firstOrNull { chapter ->
        chapter.title.matchesAnchor(anchor) || chapter.summary.orEmpty().matchesAnchor(anchor)
    }
    if (chapterMatch != null) return volumeById[chapterMatch.volumeId]

    val chaptersById = chaptersByVolume.values.flatten().associateBy { it.id }
    val sceneMatch = scenesByChapter.values.flatten().firstOrNull { scene ->
        scene.title.orEmpty().matchesAnchor(anchor) || scene.summary.orEmpty().matchesAnchor(anchor)
    }
    if (sceneMatch != null) {
        val chapter = chaptersById[sceneMatch.chapterId]
        if (chapter != null) return volumeById[chapter.volumeId]
    }

    return volumes.first()
}

private fun selectFocusChapters(
    chapters: List<StoryChapterRecord>,
    scenesByChapter: Map<String, List<StorySceneRecord>>,
    anchor: String,
): List<StoryChapterRecord> {
    if (chapters.isEmpty()) return emptyList()
    if (anchor.isBlank()) return chapters.take(maxFocusChapters)

    val sceneAnchoredChapterIds = scenesByChapter
        .filterValues { scenes ->
            scenes.any { scene ->
                scene.title.orEmpty().matchesAnchor(anchor) || scene.summary.orEmpty().matchesAnchor(anchor)
            }
        }
        .keys

    val anchored = chapters.filter { chapter ->
        chapter.id in sceneAnchoredChapterIds ||
            chapter.title.matchesAnchor(anchor) ||
            chapter.summary.orEmpty().matchesAnchor(anchor)
    }
    return if (anchored.isEmpty()) chapters.take(maxFocusChapters) else anchored.take(maxFocusChapters)
}

private fun selectFocusScenes(
    scenes: List<StorySceneRecord>,
    anchor: String,
): List<StorySceneRecord> {
    if (scenes.isEmpty()) return emptyList()
    if (anchor.isBlank()) return scenes.take(maxFocusScenes)
    val anchored = scenes.filter { scene ->
        scene.title.orEmpty().matchesAnchor(anchor) || scene.summary.orEmpty().matchesAnchor(anchor)
    }
    return if (anchored.isEmpty()) scenes.take(maxFocusScenes) else anchored.take(maxFocusScenes)
}

private fun String.matchesAnchor(anchor: String): Boolean = anchor.isNotBlank() && lowercase().contains(anchor)
