package com.ead.dispatch.sample.data.db.type

enum class ContentStatus {
    DRAFT,
    IN_PROGRESS,
    FINAL,
    ARCHIVED;

    companion object {
        fun fromDb(value: String): ContentStatus =
            entries.first { it.name == value }
    }
}
