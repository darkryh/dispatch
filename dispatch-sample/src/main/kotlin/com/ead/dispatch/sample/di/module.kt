package com.ead.dispatch.sample.di

import com.ead.dispatch.koin.dispatchModule
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.DatabaseRuntime
import com.ead.dispatch.sample.data.db.entities.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.SessionManager
import com.ead.dispatch.sample.domain.agents.ChatAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.KoogChatAgent
import com.ead.dispatch.sample.domain.agents.story_agent.KoogStoryAgent
import com.ead.dispatch.sample.domain.agents.StoryAgent
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryChapterMemorySummarizer
import com.ead.dispatch.sample.domain.agents.story_agent.memory.policy.KoogStorySummarizationPolicy
import com.ead.dispatch.sample.domain.agents.story_agent.memory.policy.StorySummarizationPolicy
import com.ead.dispatch.sample.domain.agents.story_agent.memory.service.StoryContinuityMemoryService
import com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.StoryChapterMemoryKoogSummarizer
import com.ead.dispatch.sample.domain.agents.internal.character_agent.CharacterAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatAgentEmbedder
import com.ead.dispatch.sample.domain.agents.internal.artifact_agent.ArtifactAgent
import com.ead.dispatch.sample.domain.agents.internal.culture_agent.CultureAgent
import com.ead.dispatch.sample.domain.agents.internal.event_agent.EventAgent
import com.ead.dispatch.sample.domain.agents.internal.location_agent.LocationAgent
import com.ead.dispatch.sample.domain.agents.internal.location_feature_agent.LocationFeatureAgent
import com.ead.dispatch.sample.domain.agents.internal.organization_agent.OrganizationAgent
import com.ead.dispatch.sample.domain.agents.internal.relationship_agent.RelationshipAgent
import com.ead.dispatch.sample.domain.agents.internal.timeline_agent.TimelineAgent
import com.ead.dispatch.sample.domain.agents.internal.world_rule_agent.WorldRuleAgent
import com.ead.dispatch.sample.domain.agents.tools.StoryDraftTools
import com.ead.dispatch.sample.domain.embedding.EmbeddingIndexService
import com.ead.dispatch.sample.domain.embedding.EmbeddingReindexer
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.dispatch.sample.domain.export.StoryExportService
import com.ead.dispatch.sample.presentation.characters.CharacterViewModel
import com.ead.dispatch.sample.presentation.chat_mode.chat.ChatViewModel
import com.ead.dispatch.sample.presentation.chat_mode.story.ChapterListViewModel
import com.ead.dispatch.sample.presentation.chat_mode.story.SceneListViewModel
import com.ead.dispatch.sample.presentation.library.arcs.ArcEditorViewModel
import com.ead.dispatch.sample.presentation.library.arcs.ArcListViewModel
import com.ead.dispatch.sample.presentation.library.artifacts.ArtifactListViewModel
import com.ead.dispatch.sample.presentation.library.artifacts.ArtifactEditorViewModel
import com.ead.dispatch.sample.presentation.library.characters.CharacterListViewModel
import com.ead.dispatch.sample.presentation.library.cultures.CultureListViewModel
import com.ead.dispatch.sample.presentation.library.cultures.CultureEditorViewModel
import com.ead.dispatch.sample.presentation.library.events.EventListViewModel
import com.ead.dispatch.sample.presentation.library.events.EventEditorViewModel
import com.ead.dispatch.sample.presentation.library.location_features.LocationFeatureListViewModel
import com.ead.dispatch.sample.presentation.library.location_features.LocationFeatureEditorViewModel
import com.ead.dispatch.sample.presentation.library.locations.LocationListViewModel
import com.ead.dispatch.sample.presentation.library.locations.LocationEditorViewModel
import com.ead.dispatch.sample.presentation.library.organizations.OrganizationListViewModel
import com.ead.dispatch.sample.presentation.library.organizations.OrganizationEditorViewModel
import com.ead.dispatch.sample.presentation.library.relationships.RelationshipListViewModel
import com.ead.dispatch.sample.presentation.library.relationships.RelationshipEditorViewModel
import com.ead.dispatch.sample.presentation.library.timeline.TimelineListViewModel
import com.ead.dispatch.sample.presentation.library.timeline.TimelineEditorViewModel
import com.ead.dispatch.sample.presentation.library.world_rules.WorldRuleListViewModel
import com.ead.dispatch.sample.presentation.library.world_rules.WorldRuleEditorViewModel
import com.ead.dispatch.sample.presentation.session.SessionViewModel
import com.ead.dispatch.sample.presentation.chat_mode.story.StoryChatViewModel
import com.ead.dispatch.sample.presentation.chat_mode.story.VolumeListViewModel

val module = dispatchModule {

    single { DispatchDatabaseFactory() }
    single { DatabaseRuntime(factory = get()) }
    single { ChatAgentEmbedder() }
    single { EmbeddingIndexService(embedderProvider = get()) }
    single { EmbeddingReindexer(repository = get()) }
    single { StructuredIndexRepository(databaseRuntime = get(), embeddingIndexService = get()) }
    single { RagContextService(repository = get(), embeddingIndexService = get()) }
    single<StoryChapterMemorySummarizer> { StoryChapterMemoryKoogSummarizer() }
    single<StorySummarizationPolicy> { KoogStorySummarizationPolicy() }
    single { StoryContinuityMemoryService(repository = get(), summarizer = get()) }
    single { StoryDraftTools(repository = get(), continuityMemoryService = get(), summarizationPolicy = get()) }
    single { StoryExportService(repository = get()) }
    single { CommandManager() }
    single { SessionManager(repository = get()) }
    single<ChatAgent> {
        KoogChatAgent(
            repository = get(),
            ragContextService = get(),
        )
    }
    single<StoryAgent> {
        KoogStoryAgent(
            repository = get(),
            ragContextService = get(),
            continuityMemoryService = get(),
            storyDraftTools = get(),
        )
    }
    single { CharacterAgent() }
    single { LocationAgent() }
    single { WorldRuleAgent() }
    single { CultureAgent() }
    single { EventAgent() }
    single { OrganizationAgent() }
    single { RelationshipAgent() }
    single { LocationFeatureAgent() }
    single { ArtifactAgent() }
    single { TimelineAgent() }

    viewModel { (savedStateHandle: SavedStateHandle) ->
        ChatViewModel(
            commandManager = get(),
            sessionManager = get(),
            repository = get(),
            chatAgent = get(),
            storyAgent = get(),
            storyDraftTools = get(),
            storyExportService = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        CharacterViewModel(
            repository = get(),
            characterAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { SessionViewModel(sessionManager = get()) }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        LocationEditorViewModel(
            repository = get(),
            locationAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        ArcEditorViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        WorldRuleEditorViewModel(
            repository = get(),
            worldRuleAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        CultureEditorViewModel(
            repository = get(),
            cultureAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        EventEditorViewModel(
            repository = get(),
            eventAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        OrganizationEditorViewModel(
            repository = get(),
            organizationAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        RelationshipEditorViewModel(
            repository = get(),
            relationshipAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        LocationFeatureEditorViewModel(
            repository = get(),
            locationFeatureAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        ArtifactEditorViewModel(
            repository = get(),
            artifactAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        TimelineEditorViewModel(
            repository = get(),
            timelineAgent = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        CharacterListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        LocationListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        ArcListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        WorldRuleListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        CultureListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        EventListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        OrganizationListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        RelationshipListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        LocationFeatureListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        ArtifactListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        TimelineListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        StoryChatViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        VolumeListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        ChapterListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
    viewModel { (savedStateHandle: SavedStateHandle) ->
        SceneListViewModel(
            repository = get(),
            savedStateHandle = savedStateHandle,
        )
    }
}
