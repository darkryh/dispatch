package com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.prompt

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.model.StoryChapterMemorySummarizeRequest

fun storyChapterMemorySummarizerPrompt(
    request: StoryChapterMemorySummarizeRequest,
): Prompt = prompt("story-memory-summarizer") {
    system {
        markdown {
            h2("Role")
            +"Summarize approved chapter canon into compact delta memory for continuity."
            br()
            +"Return only structured fields."
            br()

            h2("Output Rules")
            +"summaryDelta: one compact statement of what changed in canon this chapter (<= 220 chars)."
            br()
            +"newFacts: 0-3 concise canonical additions introduced in this chapter."
            br()
            +"resolvedThreads: 0-2 concise tensions/questions that became resolved."
            br()
            +"openThreads: 0-3 concise tensions/questions still unresolved."
            br()
            +"continuityRisks: 0-2 concise potential continuity risks/contradictions."
            br()
            +"warnings: 0-2 concise continuity warnings worth surfacing to the writer."
            br()
            +"confidence: HIGH/MEDIUM/LOW indicating extraction confidence."
            br()
            +"isUsable: true only when output is reliable enough for persistence."
            br()
            +"Do not invent facts not present in inputs."
            br()
            +"Do not prefix values with tags like 'Fact:' or 'Risk:'."
            br()
        }
    }
    user {
        markdown {
            +"Chapter ${request.chapterNumber}: ${request.chapterTitle}"
            br()
            +"Chapter summary: ${request.chapterSummary ?: "none"}"
            br()
            +"Key beats:"
            br()
            if (request.keyBeats.isEmpty()) {
                +"(none)"
                br()
            } else {
                request.keyBeats.forEach { beat ->
                    +" - ${beat.compact(120)}"
                    br()
                }
            }
            +"Approved excerpt:"
            br()
            +request.approvedTextExcerpt
            br()
        }
    }
}

private fun String.compact(maxChars: Int): String {
    val normalized = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= maxChars) normalized else normalized.take(maxChars) + "..."
}
