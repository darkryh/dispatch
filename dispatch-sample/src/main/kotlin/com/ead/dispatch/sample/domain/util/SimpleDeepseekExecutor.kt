package com.ead.dispatch.sample.domain.util

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor


fun simpleDeepseekExecutor(apiToken : String) : PromptExecutor =
    SingleLLMPromptExecutor(DeepSeekLLMClient(apiToken))