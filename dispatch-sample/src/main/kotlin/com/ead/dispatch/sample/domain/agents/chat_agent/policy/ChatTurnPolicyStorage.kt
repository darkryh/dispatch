package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest

private val chatTurnPolicyKey: AIAgentStorageKey<ChatTurnPolicy> =
    AIAgentStorageKey("dispatch.chat.turn.policy")

private val chatTurnMetricsKey: AIAgentStorageKey<ChatTurnMetrics> =
    AIAgentStorageKey("dispatch.chat.turn.metrics")

private val loadedChatPreferencesContextKey: AIAgentStorageKey<String> =
    AIAgentStorageKey("dispatch.chat.turn.loaded_chat_preferences_context")

private val lastSavedPreferenceHashKey: AIAgentStorageKey<String> =
    AIAgentStorageKey("dispatch.chat.turn.last_saved_preference_hash")

private val chatTurnRequestKey: AIAgentStorageKey<ChatRequest> =
    AIAgentStorageKey("dispatch.chat.turn.request")

suspend fun AIAgentGraphContextBase.storeChatTurnPolicy(policy: ChatTurnPolicy) {
    store(chatTurnPolicyKey, policy)
    store(chatTurnMetricsKey, policy.toMetrics())
}

suspend fun AIAgentGraphContextBase.currentChatTurnPolicy(): ChatTurnPolicy? =
    get(chatTurnPolicyKey) as? ChatTurnPolicy

suspend fun AIAgentGraphContextBase.currentChatTurnMetrics(): ChatTurnMetrics? =
    get(chatTurnMetricsKey) as? ChatTurnMetrics

suspend fun AIAgentGraphContextBase.updateChatTurnMetrics(update: (ChatTurnMetrics) -> Unit) {
    val metrics = currentChatTurnMetrics() ?: return
    update(metrics)
    store(chatTurnMetricsKey, metrics)
}

suspend fun AIAgentGraphContextBase.storeLoadedChatPreferencesContext(context: String?) {
    if (context.isNullOrBlank()) {
        remove(loadedChatPreferencesContextKey)
        return
    }
    store(loadedChatPreferencesContextKey, context)
}

suspend fun AIAgentGraphContextBase.currentLoadedChatPreferencesContext(): String? =
    get(loadedChatPreferencesContextKey) as? String

suspend fun AIAgentGraphContextBase.storeLastSavedPreferenceHash(hash: String?) {
    if (hash.isNullOrBlank()) {
        remove(lastSavedPreferenceHashKey)
        return
    }
    store(lastSavedPreferenceHashKey, hash)
}

suspend fun AIAgentGraphContextBase.currentLastSavedPreferenceHash(): String? =
    get(lastSavedPreferenceHashKey) as? String

suspend fun AIAgentGraphContextBase.storeChatTurnRequest(request: ChatRequest) {
    store(chatTurnRequestKey, request)
}

suspend fun AIAgentGraphContextBase.currentChatTurnRequest(): ChatRequest? =
    get(chatTurnRequestKey) as? ChatRequest
