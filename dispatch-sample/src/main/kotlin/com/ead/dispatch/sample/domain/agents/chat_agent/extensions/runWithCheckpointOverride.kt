package com.ead.dispatch.sample.domain.agents.chat_agent.extensions

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.isTombstone
import com.ead.dispatch.sample.domain.Storage
import kotlinx.datetime.Clock
import kotlin.reflect.typeOf

/**
 * Runs an agent after updating the latest checkpoint input so RollbackStrategy.Default
 * replays the new user input instead of the previously stored one.
 */
internal suspend inline fun <reified Input, Output> AIAgent<Input, Output>.runWithStartCheckpoint(
    agentId: String,
    input: Input,
): Output {
    overrideCheckpointInput(agentId, input)
    return run(input)
}

/**
 * Rewrites the latest checkpoint's input payload for a given agent id.
 * This keeps the existing history but forces the next run to use the new input.
 */
@OptIn(ExperimentalStdlibApi::class, InternalAgentsApi::class)
internal suspend inline fun <reified Input> overrideCheckpointInput(agentId: String, input: Input) {
    val latest = Storage.provider.getLatestCheckpoint(agentId) ?: return
    if (latest.isTombstone()) return

    val inputJson = SerializationUtils.encodeDataToJsonElementOrNull(input, typeOf<Input>())
        ?: return

    val updated = AgentCheckpointData(
        checkpointId = latest.checkpointId,
        createdAt = Clock.System.now(),
        nodePath = latest.nodePath,
        lastInput = inputJson,
        messageHistory = latest.messageHistory,
        version = latest.version + 1,
        properties = latest.properties
    )

    Storage.provider.saveCheckpoint(agentId, updated)
}
