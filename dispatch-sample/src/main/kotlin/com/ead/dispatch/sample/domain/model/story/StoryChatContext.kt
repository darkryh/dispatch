package com.ead.dispatch.sample.domain.model.story

import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.db.entities.StoryFactRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationRecord
import com.ead.dispatch.sample.data.db.entities.StoryRecord

data class StoryChatContext(
    val story: StoryRecord?,
    val characters: List<StoryCharacterRecord> = emptyList(),
    val locations: List<StoryLocationRecord> = emptyList(),
    val arcs: List<StoryArcRecord> = emptyList(),
    val facts: List<StoryFactRecord> = emptyList(),
)
