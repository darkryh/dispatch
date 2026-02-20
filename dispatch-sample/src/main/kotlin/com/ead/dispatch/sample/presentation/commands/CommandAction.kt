package com.ead.dispatch.sample.presentation.commands

import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.domain.export.StoryExportFormat
import com.ead.dispatch.sample.domain.export.StoryExportOutputTarget
import com.ead.dispatch.sample.domain.export.StoryExportScopeType

sealed class CommandAction {
    object ClearContext : CommandAction()
    data class OpenEntityList(val type: EntityOptionType) : CommandAction()
    object OpenStoryChat : CommandAction()
    data class ExportStory(
        val scopeType: StoryExportScopeType = StoryExportScopeType.STORY,
        val scopeId: String? = null,
        val format: StoryExportFormat = StoryExportFormat.MD,
        val outputTarget: StoryExportOutputTarget = StoryExportOutputTarget.CWD,
        val outputPath: String? = null,
    ) : CommandAction()
    data class ShowError(val message: String) : CommandAction()
}
