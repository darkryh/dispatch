package com.ead.dispatch.sample.data.db.type

import kotlinx.serialization.Serializable

@Serializable
enum class ContentType {
    MARKDOWN,
    DOCX,
    PDF,
    TEXT;

    companion object {
        fun fromDb(value: String): ContentType =
            entries.first { it.name == value }
    }
}
