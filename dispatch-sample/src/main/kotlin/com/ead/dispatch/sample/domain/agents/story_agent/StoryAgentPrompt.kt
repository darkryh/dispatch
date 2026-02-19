package com.ead.dispatch.sample.domain.agents.story_agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StorySceneRecord
import com.ead.dispatch.sample.data.db.entities.StoryVolumeRecord
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryContinuitySnapshot
import com.ead.dispatch.sample.domain.agents.story_agent.policy.StoryDecisionPath
import com.ead.dispatch.sample.domain.agents.story_agent.policy.StoryTurnPolicy
import com.ead.dispatch.sample.domain.embedding.RagContextChunk
import com.ead.dispatch.sample.domain.model.story.StoryChatContext
import com.ead.dispatch.sample.domain.model.story.StoryModeContext

private const val maxRagChunks = 4
private const val maxRagCharsPerChunk = 360
private const val maxVolumes = 4
private const val maxChapters = 8
private const val maxScenes = 12

fun storyAgentPrompt(
    storyModeContext: StoryModeContext,
    chatContext: StoryChatContext,
    inputRequest: StoryRequest,
    ragContext: List<RagContextChunk>,
    turnPolicy: StoryTurnPolicy,
    continuityMemory: StoryContinuitySnapshot? = null,
): Prompt = prompt("story-agent") {
    val volumes = storyModeContext.volumes.sortedBy { it.number }
    val chaptersByVolume = storyModeContext.chapters.groupBy { it.volumeId }
    val scenesByChapter = storyModeContext.scenes.groupBy { it.chapterId }
    val story = storyModeContext.story ?: chatContext.story

    system {
        markdown {
            h2("Role")
            +"You are the STORY mode agent for long-form writing."
            br()
            +"Focus on progressive structure and prose flow across volumes, chapters, and scenes."
            br()
            +"CHAT mode handles worldbuilding elements; STORY mode handles story execution."
            br()

            h2("Operational Rules")
            +"Always inspect story structure with getStoryModeContext before write actions."
            br()
            +"For chapter text revisions, prefer proposeChapterDraftEdit then applyChapterDraftProposal after confirmation."
            br()
            +"If user rejects a pending chapter proposal, call deleteChapterDraftProposal."
            br()
            +"Run validateChapterDraft after major draft updates and report any warnings clearly."
            br()
            +"For destructive operations (delete volume/chapter with children), require explicit user confirmation before force=true."
            br()
            +"When request is ambiguous, ask one short clarifying question."
            br()
            +"Never fabricate tool outputs or ids."
            br()

            h2("Turn Policy")
            +"Intent class: ${turnPolicy.intentClass.name}"
            br()
            +"Decision path: ${turnPolicy.decisionPath.name}"
            br()
            +"Write tools allowed: ${turnPolicy.allowWriteTools}"
            br()
            +"Selector required for destructive write: ${turnPolicy.requireSelectorForDestructive}"
            br()
            if (turnPolicy.decisionPath == StoryDecisionPath.FOLLOW_UP) {
                +"Ask one focused follow-up and stop."
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

            h3("Current Structure")
            if (volumes.isEmpty()) {
                +"(no volumes yet)"
                br()
            } else {
                volumes.take(maxVolumes).forEach { volume ->
                    +volume.summaryLine()
                    br()
                    val chapters = chaptersByVolume[volume.id].orEmpty().sortedBy { it.number }
                    if (chapters.isEmpty()) {
                        +"  - no chapters"
                        br()
                    } else {
                        chapters.take(maxChapters).forEach { chapter ->
                            +chapter.summaryLine()
                            br()
                            val scenes = scenesByChapter[chapter.id].orEmpty().sortedBy { it.number }
                            if (scenes.isNotEmpty()) {
                                scenes.take(maxScenes).forEach { scene ->
                                    +scene.summaryLine()
                                    br()
                                }
                                if (scenes.size > maxScenes) {
                                    +"    - (+${scenes.size - maxScenes} more scenes)"
                                    br()
                                }
                            }
                        }
                        if (chapters.size > maxChapters) {
                            +"  - (+${chapters.size - maxChapters} more chapters)"
                            br()
                        }
                    }
                }
                if (volumes.size > maxVolumes) {
                    +"(+${volumes.size - maxVolumes} more volumes)"
                    br()
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
                +"Rolling: ${continuityMemory.rollingSummary.ifBlank { "none" }}"
                br()
                if (continuityMemory.activeThreads.isEmpty()) {
                    +"Active threads: (none)"
                    br()
                } else {
                    +"Active threads:"
                    br()
                    continuityMemory.activeThreads.take(3).forEach { thread ->
                        +"  - ${thread.compact(90)}"
                        br()
                    }
                }
                if (continuityMemory.recentChapters.isNotEmpty()) {
                    +"Recent approved chapters:"
                    br()
                    continuityMemory.recentChapters.forEachIndexed { index, chapterMemory ->
                        +"  ${index + 1}. ${chapterMemory.summaryShort.compact(120)}"
                        br()
                    }
                }
                if (continuityMemory.continuityWarnings.isNotEmpty()) {
                    +"Warnings:"
                    br()
                    continuityMemory.continuityWarnings.take(2).forEach { warning ->
                        +"  - ${warning.compact(90)}"
                        br()
                    }
                }
            }

            h2("Response Style")
            +"Be concise and production-oriented."
            br()
            +"After tool execution, report what changed and one optional next step."
            br()
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
