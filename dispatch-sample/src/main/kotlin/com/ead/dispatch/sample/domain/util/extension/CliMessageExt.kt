package com.ead.dispatch.sample.domain.util.extension

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.domain.model.message.CliMessage
import com.ead.dispatch.sample.domain.model.message.CliMessageRole

// ============================================================================
// CliMessage <-> KOOG Message Conversion
// ============================================================================

fun CliMessage.toMessage(): Message {
    return when (this.role) {
        CliMessageRole.USER -> Message.User(
            content = this.data,
            metaInfo = RequestMetaInfo(timestamp = this.timestamp)
        )
        CliMessageRole.ASSISTANT -> Message.Assistant(
            content = this.data,
            metaInfo = ResponseMetaInfo(timestamp = this.timestamp)
        )
        CliMessageRole.SYSTEM -> Message.System(
            content = this.data,
            metaInfo = RequestMetaInfo(timestamp = this.timestamp)
        )
    }
}

fun Message.toCliMessage(): CliMessage {
    return when (this.role) {
        Message.Role.System -> CliMessage(data = this.content, role = CliMessageRole.SYSTEM)
        Message.Role.User -> CliMessage(data = this.content, role = CliMessageRole.USER)
        Message.Role.Assistant -> CliMessage(data = this.content, role = CliMessageRole.ASSISTANT)
        Message.Role.Reasoning -> CliMessage(data = this.content, role = CliMessageRole.SYSTEM)
        Message.Role.Tool -> CliMessage(data = this.content, role = CliMessageRole.SYSTEM)
    }
}