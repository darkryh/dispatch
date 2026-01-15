package com.ead.dispatch.sample.data.db.type

enum class RagSourceType {
    FILE,
    CHAT,
    NOTE;

    companion object {
        fun fromDb(value: String): RagSourceType =
            entries.first { it.name == value }
    }
}
