package com.ead.dispatch.sample.data.db.type

import kotlinx.serialization.Serializable

@Serializable
enum class ArcScope {
    STORY,
    VOLUME,
    CHAPTER,
    SCENE,
    CHARACTER;

    companion object {
        fun fromDb(value: String): ArcScope =
            entries.first { it.name == value }
    }
}
