package com.ead.dispatch.sample.domain.model.story

import com.ead.dispatch.sample.data.db.entities.RagDocumentRecord
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.db.entities.StorySceneRecord
import com.ead.dispatch.sample.data.db.entities.StoryVolumeRecord

data class StoryModeContext(
    val story: StoryRecord?,
    val volumes: List<StoryVolumeRecord> = emptyList(),
    val chapters: List<StoryChapterRecord> = emptyList(),
    val scenes: List<StorySceneRecord> = emptyList(),
    val ragDocuments: List<RagDocumentRecord> = emptyList(),
)
