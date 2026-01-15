package com.ead.dispatch.sample.domain.agents.chat_agent.planner

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatSystemPrompt

internal fun chatExecutionPrompt(): Prompt =
    prompt("chat-mode-executor") {
        system(ChatSystemPrompt.load())
    }
