package com.ead.dispatch.sample.presentation.library

import com.ead.dispatch.sample.domain.agents.internal.artifact_agent.ArtifactAIDraft
import com.ead.dispatch.sample.domain.agents.internal.culture_agent.CultureAIDraft
import com.ead.dispatch.sample.domain.agents.internal.event_agent.EventAIDraft
import com.ead.dispatch.sample.domain.agents.internal.location_agent.LocationAIDraft
import com.ead.dispatch.sample.domain.agents.internal.location_feature_agent.LocationFeatureAIDraft
import com.ead.dispatch.sample.domain.agents.internal.organization_agent.OrganizationAIDraft
import com.ead.dispatch.sample.domain.agents.internal.relationship_agent.RelationshipAIDraft
import com.ead.dispatch.sample.domain.agents.internal.timeline_agent.TimelineAIDraft
import com.ead.dispatch.sample.domain.agents.internal.world_rule_agent.WorldRuleAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.library.artifacts.ArtifactDraftMapper
import com.ead.dispatch.sample.presentation.library.cultures.CultureDraftMapper
import com.ead.dispatch.sample.presentation.library.events.EventDraftMapper
import com.ead.dispatch.sample.presentation.library.locations.LocationDraftMapper
import com.ead.dispatch.sample.presentation.library.location_features.LocationFeatureDraftMapper
import com.ead.dispatch.sample.presentation.library.organizations.OrganizationDraftMapper
import com.ead.dispatch.sample.presentation.library.relationships.RelationshipDraftMapper
import com.ead.dispatch.sample.presentation.library.timeline.TimelineDraftMapper
import com.ead.dispatch.sample.presentation.library.world_rules.WorldRuleDraftMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityDraftMappersTest {
    @Test
    fun `location mapper fills core fields`() {
        val draft = LocationAIDraft(
            name = "The Memory Garden",
            summary = "A quiet refuge",
            description = "Overgrown greenhouse.",
            tags = listOf("sanctuary", "secret"),
        )

        val values = LocationDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("The Memory Garden", values[EditorFieldKey.NAME]?.text)
        assertEquals("Overgrown greenhouse.", values[EditorFieldKey.DESCRIPTION]?.text)
        assertEquals("sanctuary, secret", values[EditorFieldKey.TAGS]?.text)
    }

    @Test
    fun `world rule mapper fills description`() {
        val draft = WorldRuleAIDraft(
            title = "Soul Anchoring",
            rule = "Power drains without an anchor.",
        )

        val values = WorldRuleDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("Soul Anchoring", values[EditorFieldKey.TITLE]?.text)
        assertEquals("Power drains without an anchor.", values[EditorFieldKey.DESCRIPTION]?.text)
    }

    @Test
    fun `culture mapper fills description`() {
        val draft = CultureAIDraft(
            name = "Echo Guild",
            summary = "A resonance-focused culture",
        )

        val values = CultureDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("Echo Guild", values[EditorFieldKey.NAME]?.text)
        assertEquals("A resonance-focused culture", values[EditorFieldKey.DESCRIPTION]?.text)
    }

    @Test
    fun `event mapper fills description`() {
        val draft = EventAIDraft(
            name = "Containment Breach",
            description = "A containment wing collapses.",
        )

        val values = EventDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("Containment Breach", values[EditorFieldKey.NAME]?.text)
        assertEquals("A containment wing collapses.", values[EditorFieldKey.DESCRIPTION]?.text)
    }

    @Test
    fun `organization mapper fills description`() {
        val draft = OrganizationAIDraft(
            name = "The Warden Protocol",
            summary = "A global containment authority",
        )

        val values = OrganizationDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("The Warden Protocol", values[EditorFieldKey.NAME]?.text)
        assertEquals("A global containment authority", values[EditorFieldKey.DESCRIPTION]?.text)
    }

    @Test
    fun `relationship mapper fills notes`() {
        val draft = RelationshipAIDraft(
            subjectName = "Lyra",
            subjectType = "character",
            objectName = "Dark",
            objectType = "character",
            relation = "ally",
            summary = "Protective alliance",
        )

        val values = RelationshipDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("character", values[EditorFieldKey.SUBJECT_TYPE]?.text)
        assertEquals("character", values[EditorFieldKey.OBJECT_TYPE]?.text)
        assertEquals("ally", values[EditorFieldKey.RELATION]?.text)
        assertEquals("Protective alliance · Subject: Lyra · Object: Dark", values[EditorFieldKey.NOTES]?.text)
    }

    @Test
    fun `location feature mapper fills description`() {
        val draft = LocationFeatureAIDraft(
            name = "Resonance Chamber",
            summary = "A cavern with perfect acoustics",
        )

        val values = LocationFeatureDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("Resonance Chamber", values[EditorFieldKey.NAME]?.text)
        assertEquals("A cavern with perfect acoustics", values[EditorFieldKey.DESCRIPTION]?.text)
    }

    @Test
    fun `artifact mapper fills description`() {
        val draft = ArtifactAIDraft(
            name = "Echo Lens",
            summary = "Amplifies latent powers",
            ownerType = "organization",
        )

        val values = ArtifactDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("Echo Lens", values[EditorFieldKey.NAME]?.text)
        assertEquals("Amplifies latent powers", values[EditorFieldKey.DESCRIPTION]?.text)
        assertEquals("organization", values[EditorFieldKey.OWNER_TYPE]?.text)
    }

    @Test
    fun `timeline mapper fills order`() {
        val draft = TimelineAIDraft(
            title = "Year 5: The Lockdown",
            description = "The facility seals all exits.",
            orderIndex = 5,
        )

        val values = TimelineDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("Year 5: The Lockdown", values[EditorFieldKey.TITLE]?.text)
        assertEquals("The facility seals all exits.", values[EditorFieldKey.DESCRIPTION]?.text)
        assertEquals("5", values[EditorFieldKey.ORDER_INDEX]?.text)
    }
}
