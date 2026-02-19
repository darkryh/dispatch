package com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.model.StoryChapterMemorySummarizeRequest
import com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.model.StoryChapterMemorySummaryDraft
import com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.prompt.storyChapterMemorySummarizerPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeSummarizeStoryMemory(
    name: String? = null,
): AIAgentNodeDelegate<StoryChapterMemorySummarizeRequest, Result<StructuredResponse<StoryChapterMemorySummaryDraft>>> =
    node(name ?: "summarize-story-memory") { request ->
        summarizeStoryMemory(request)
    }

suspend fun AIAgentContext.summarizeStoryMemory(
    request: StoryChapterMemorySummarizeRequest,
): Result<StructuredResponse<StoryChapterMemorySummaryDraft>> = llm.writeSession {
    this.model = AIProvider.Story.main
    rewritePrompt {
        storyChapterMemorySummarizerPrompt(request)
    }
    requestLLMStructured<StoryChapterMemorySummaryDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.Story.fixer,
            retries = 2,
        )
    )
}
