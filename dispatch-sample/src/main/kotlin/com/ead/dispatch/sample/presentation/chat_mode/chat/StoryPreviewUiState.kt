package com.ead.dispatch.sample.presentation.chat_mode.chat

import com.ead.dispatch.sample.domain.agents.tools.model.StoryDraftPreviewSnapshot

enum class StoryPreviewFocusZone {
    PAGES,
    HUNKS,
    ACTIONS,
}

data class StoryPreviewUiState(
    val snapshot: StoryDraftPreviewSnapshot,
    val focused: Boolean,
    val selectedPageIndex: Int,
    val pageCount: Int,
    val selectedPendingRangeIndex: Int,
    val selectedActionIndex: Int,
    val activeZone: StoryPreviewFocusZone,
)
