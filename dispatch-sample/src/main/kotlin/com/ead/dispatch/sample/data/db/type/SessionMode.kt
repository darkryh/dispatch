package com.ead.dispatch.sample.data.db.type

enum class SessionMode {
    CHAT,
    CHAT_STORY;

    companion object {
        fun fromDb(value: String): SessionMode =
            entries.first { it.name == value }
    }
}
