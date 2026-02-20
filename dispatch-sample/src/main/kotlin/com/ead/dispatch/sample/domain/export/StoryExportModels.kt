package com.ead.dispatch.sample.domain.export

enum class StoryExportScopeType {
    STORY,
    VOLUME,
    CHAPTER,
}

enum class StoryExportFormat {
    TXT,
    MD,
}

enum class StoryExportOutputTarget {
    CWD,
    DOWNLOADS,
    PATH,
}

data class StoryExportRequest(
    val storyId: String,
    val scopeType: StoryExportScopeType = StoryExportScopeType.STORY,
    val scopeId: String? = null,
    val format: StoryExportFormat = StoryExportFormat.MD,
    val outputTarget: StoryExportOutputTarget = StoryExportOutputTarget.CWD,
    val outputPath: String? = null,
)

data class ExportedFileInfo(
    val path: String,
    val scopeType: StoryExportScopeType,
    val scopeId: String?,
)

data class StoryExportResult(
    val rootPath: String,
    val files: List<ExportedFileInfo>,
    val warnings: List<String> = emptyList(),
)

