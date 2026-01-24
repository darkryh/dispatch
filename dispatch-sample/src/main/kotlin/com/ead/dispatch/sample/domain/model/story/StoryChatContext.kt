package com.ead.dispatch.sample.domain.model.story

import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.db.entities.StoryArtifactRecord
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.db.entities.StoryCultureRecord
import com.ead.dispatch.sample.data.db.entities.StoryEventRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationFeatureRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationRecord
import com.ead.dispatch.sample.data.db.entities.StoryOrganizationRecord
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.db.entities.StoryRelationshipRecord
import com.ead.dispatch.sample.data.db.entities.StoryTimelineEntryRecord
import com.ead.dispatch.sample.data.db.entities.StoryWorldRuleRecord
import kotlinx.serialization.Serializable

@Serializable
data class StoryChatContext(
    val story: StoryRecord?,
    val characters: OverflowList<StoryCharacterRecord> = emptyOverflowList(),
    val locations: OverflowList<StoryLocationRecord> = emptyOverflowList(),
    val arcs: OverflowList<StoryArcRecord> = emptyOverflowList(),
    val worldRules: OverflowList<StoryWorldRuleRecord> = emptyOverflowList(),
    val cultures: OverflowList<StoryCultureRecord> = emptyOverflowList(),
    val events: OverflowList<StoryEventRecord> = emptyOverflowList(),
    val organizations: OverflowList<StoryOrganizationRecord> = emptyOverflowList(),
    val relationships: OverflowList<StoryRelationshipRecord> = emptyOverflowList(),
    val locationFeatures: OverflowList<StoryLocationFeatureRecord> = emptyOverflowList(),
    val artifacts: OverflowList<StoryArtifactRecord> = emptyOverflowList(),
    val timelineEntries: OverflowList<StoryTimelineEntryRecord> = emptyOverflowList(),
)
