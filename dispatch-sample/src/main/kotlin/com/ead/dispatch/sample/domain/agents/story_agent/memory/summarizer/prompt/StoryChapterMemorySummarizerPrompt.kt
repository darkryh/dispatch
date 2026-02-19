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
            +"Summarize approved chapter canon for continuity memory."
            br()
            +"Return only structured fields."
            br()

            h2("Output Rules")
            +"summaryShort: one compact continuity summary (1-2 sentences, <= 280 chars)."
            br()
            +"unresolvedThreads: 0-3 short bullet-like strings for open questions/tensions."
            br()
            +"Do not invent facts not present in inputs."
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
