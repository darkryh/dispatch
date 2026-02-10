package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey

private val chatTurnPolicyKey: AIAgentStorageKey<ChatTurnPolicy> =
    AIAgentStorageKey("dispatch.chat.turn.policy")

private val chatTurnMetricsKey: AIAgentStorageKey<ChatTurnMetrics> =
    AIAgentStorageKey("dispatch.chat.turn.metrics")

private val loadedUserPreferencesContextKey: AIAgentStorageKey<String> =
    AIAgentStorageKey("dispatch.chat.turn.loaded_user_preferences_context")

private val lastSavedPreferenceHashKey: AIAgentStorageKey<String> =
    AIAgentStorageKey("dispatch.chat.turn.last_saved_preference_hash")

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

suspend fun AIAgentGraphContextBase.storeLoadedUserPreferencesContext(context: String?) {
    if (context.isNullOrBlank()) {
        remove(loadedUserPreferencesContextKey)
        return
    }
    store(loadedUserPreferencesContextKey, context)
}

suspend fun AIAgentGraphContextBase.currentLoadedUserPreferencesContext(): String? =
    get(loadedUserPreferencesContextKey) as? String

suspend fun AIAgentGraphContextBase.storeLastSavedPreferenceHash(hash: String?) {
    if (hash.isNullOrBlank()) {
        remove(lastSavedPreferenceHashKey)
        return
    }
    store(lastSavedPreferenceHashKey, hash)
}

suspend fun AIAgentGraphContextBase.currentLastSavedPreferenceHash(): String? =
    get(lastSavedPreferenceHashKey) as? String
