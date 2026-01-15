package com.ead.dispatch.sample.data.db

import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationRecord
import com.ead.dispatch.sample.data.db.entities.StorySceneRecord
import com.ead.dispatch.sample.data.db.type.ArcScope

object StoryRecordValidator {

    fun validateChapter(record: StoryChapterRecord): List<String> = buildList {
        if (record.number < 1) add("chapter.number must be >= 1")
        if (record.title.isBlank()) add("chapter.title must not be blank")
        val content = record.content
        if (content?.wordCount != null && content.wordCount < 0) {
            add("chapter.wordCount must be >= 0 when set")
        }
        if (content?.ref != null && content.ref.isBlank()) {
            add("chapter.contentRef must not be blank when set")
        }
        if (content?.range != null && content.ref.isNullOrBlank()) {
            add("chapter.contentRange requires chapter.contentRef")
        }
        if (content?.checksum != null && content.ref.isNullOrBlank()) {
            add("chapter.contentChecksum requires chapter.contentRef")
        }
        if (content?.updatedAt != null && content.ref.isNullOrBlank()) {
            add("chapter.contentUpdatedAt requires chapter.contentRef")
        }
    }

    fun validateScene(record: StorySceneRecord): List<String> = buildList {
        if (record.number < 1) add("scene.number must be >= 1")
        if (record.title != null && record.title.isBlank()) {
            add("scene.title must not be blank when set")
        }
        val context = record.context
        if (context?.range != null && context.range.isBlank()) {
            add("scene.contentRange must not be blank when set")
        }
        if (context?.locationId != null && context.locationId.isBlank()) {
            add("scene.locationId must not be blank when set")
        }
    }

    fun validateLocation(record: StoryLocationRecord): List<String> = buildList {
        if (record.profile.name.isBlank()) add("location.name must not be blank")
    }

    fun validateArc(record: StoryArcRecord): List<String> = buildList {
        if (record.title.isBlank()) add("arc.title must not be blank")
        if (record.scopeType == ArcScope.STORY && record.scopeId != null) {
            add("arc.scopeId must be null when scopeType is STORY")
        }
        if (record.scopeType != ArcScope.STORY && record.scopeId.isNullOrBlank()) {
            add("arc.scopeId is required when scopeType is ${record.scopeType.name}")
        }
    }
}
