package com.ead.dispatch.sample.domain.export

import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StoryVolumeRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.content.ChapterContentStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class StoryExportService(
    private val repository: StructuredIndexRepository,
    private val contentStore: ChapterContentStore = ChapterContentStore(),
) {
    suspend fun export(request: StoryExportRequest): Result<StoryExportResult> = runCatching {
        val story = requireNotNull(repository.getStoryById(request.storyId)) {
            "Story '${request.storyId}' was not found."
        }
        val outputBase = StoryExportPathResolver.resolve(request).getOrThrow()
        val storyName = sanitizeName(story.title?.takeIf { it.isNotBlank() } ?: request.storyId)
        val storyRoot = outputBase.resolve(storyName)
        Files.createDirectories(storyRoot)

        val warnings = mutableListOf<String>()
        val files = mutableListOf<ExportedFileInfo>()
        val extension = when (request.format) {
            StoryExportFormat.TXT -> "txt"
            StoryExportFormat.MD -> "md"
        }

        when (request.scopeType) {
            StoryExportScopeType.CHAPTER -> {
                val chapterId = requireNotNull(request.scopeId) { "scopeId is required for CHAPTER export." }
                val chapter = requireNotNull(repository.getChapterById(chapterId)) {
                    "Chapter '$chapterId' was not found."
                }
                val volume = requireNotNull(repository.getVolumeById(chapter.volumeId)) {
                    "Volume '${chapter.volumeId}' was not found for chapter '$chapterId'."
                }
                val chapterText = resolveChapterText(chapter, warnings)
                val chapterFolder = ensureChapterFolder(storyRoot, volume, chapter)
                val path = chapterFolder.resolve("chapter.$extension")
                writeFile(path, chapterText)
                files += ExportedFileInfo(
                    path = path.toAbsolutePath().normalize().toString(),
                    scopeType = StoryExportScopeType.CHAPTER,
                    scopeId = chapter.id,
                )
            }

            StoryExportScopeType.VOLUME -> {
                val volumeId = requireNotNull(request.scopeId) { "scopeId is required for VOLUME export." }
                val volume = requireNotNull(repository.getVolumeById(volumeId)) {
                    "Volume '$volumeId' was not found."
                }
                val chapters = repository.getChaptersByVolume(volume.id).sortedBy { it.number }
                val volumeFolder = ensureVolumeFolder(storyRoot, volume)
                val combined = renderVolumeCombined(request.format, volume, chapters, warnings)
                val combinedPath = volumeFolder.resolve("volume_${volume.number.twoDigits()}.$extension")
                writeFile(combinedPath, combined)
                files += ExportedFileInfo(
                    path = combinedPath.toAbsolutePath().normalize().toString(),
                    scopeType = StoryExportScopeType.VOLUME,
                    scopeId = volume.id,
                )
                chapters.forEach { chapter ->
                    val chapterFolder = ensureChapterFolder(storyRoot, volume, chapter)
                    val chapterPath = chapterFolder.resolve("chapter.$extension")
                    writeFile(chapterPath, resolveChapterText(chapter, warnings))
                    files += ExportedFileInfo(
                        path = chapterPath.toAbsolutePath().normalize().toString(),
                        scopeType = StoryExportScopeType.CHAPTER,
                        scopeId = chapter.id,
                    )
                }
            }

            StoryExportScopeType.STORY -> {
                val volumes = repository.getVolumesByStory(request.storyId).sortedBy { it.number }
                val combined = renderStoryCombined(request.format, storyRoot.fileName.toString(), volumes, warnings)
                val combinedPath = storyRoot.resolve("story.$extension")
                writeFile(combinedPath, combined)
                files += ExportedFileInfo(
                    path = combinedPath.toAbsolutePath().normalize().toString(),
                    scopeType = StoryExportScopeType.STORY,
                    scopeId = request.storyId,
                )
                volumes.forEach { volume ->
                    val volumeFolder = ensureVolumeFolder(storyRoot, volume)
                    val chapters = repository.getChaptersByVolume(volume.id).sortedBy { it.number }
                    val volumeCombined = renderVolumeCombined(request.format, volume, chapters, warnings)
                    val volumePath = volumeFolder.resolve("volume_${volume.number.twoDigits()}.$extension")
                    writeFile(volumePath, volumeCombined)
                    files += ExportedFileInfo(
                        path = volumePath.toAbsolutePath().normalize().toString(),
                        scopeType = StoryExportScopeType.VOLUME,
                        scopeId = volume.id,
                    )
                    chapters.forEach { chapter ->
                        val chapterFolder = ensureChapterFolder(storyRoot, volume, chapter)
                        val chapterPath = chapterFolder.resolve("chapter.$extension")
                        writeFile(chapterPath, resolveChapterText(chapter, warnings))
                        files += ExportedFileInfo(
                            path = chapterPath.toAbsolutePath().normalize().toString(),
                            scopeType = StoryExportScopeType.CHAPTER,
                            scopeId = chapter.id,
                        )
                    }
                }
            }
        }

        StoryExportResult(
            rootPath = storyRoot.toAbsolutePath().normalize().toString(),
            files = files,
            warnings = warnings.distinct(),
        )
    }

    private suspend fun renderStoryCombined(
        format: StoryExportFormat,
        storyTitle: String,
        volumes: List<StoryVolumeRecord>,
        warnings: MutableList<String>,
    ): String {
        return when (format) {
            StoryExportFormat.TXT -> buildString {
                appendLine(storyTitle)
                appendLine("=".repeat(storyTitle.length.coerceAtLeast(1)))
                appendLine()
                volumes.forEach { volume ->
                    appendLine("Volume ${volume.number}: ${volume.title}")
                    appendLine("-".repeat(("Volume ${volume.number}: ${volume.title}").length))
                    appendLine()
                    val chapters = repository.getChaptersByVolume(volume.id).sortedBy { it.number }
                    chapters.forEach { chapter ->
                        appendLine("Chapter ${chapter.number}: ${chapter.title}")
                        appendLine()
                        appendLine(resolveChapterText(chapter, warnings))
                        appendLine()
                    }
                }
            }
            StoryExportFormat.MD -> buildString {
                appendLine("# $storyTitle")
                appendLine()
                volumes.forEach { volume ->
                    appendLine("## Volume ${volume.number}: ${volume.title}")
                    appendLine()
                    val chapters = repository.getChaptersByVolume(volume.id).sortedBy { it.number }
                    chapters.forEach { chapter ->
                        appendLine("### Chapter ${chapter.number}: ${chapter.title}")
                        appendLine()
                        appendLine(resolveChapterText(chapter, warnings))
                        appendLine()
                    }
                }
            }
        }
    }

    private suspend fun renderVolumeCombined(
        format: StoryExportFormat,
        volume: StoryVolumeRecord,
        chapters: List<StoryChapterRecord>,
        warnings: MutableList<String>,
    ): String {
        return when (format) {
            StoryExportFormat.TXT -> buildString {
                val title = "Volume ${volume.number}: ${volume.title}"
                appendLine(title)
                appendLine("=".repeat(title.length.coerceAtLeast(1)))
                appendLine()
                chapters.forEach { chapter ->
                    appendLine("Chapter ${chapter.number}: ${chapter.title}")
                    appendLine()
                    appendLine(resolveChapterText(chapter, warnings))
                    appendLine()
                }
            }
            StoryExportFormat.MD -> buildString {
                appendLine("# Volume ${volume.number}: ${volume.title}")
                appendLine()
                chapters.forEach { chapter ->
                    appendLine("## Chapter ${chapter.number}: ${chapter.title}")
                    appendLine()
                    appendLine(resolveChapterText(chapter, warnings))
                    appendLine()
                }
            }
        }
    }

    private suspend fun resolveChapterText(
        chapter: StoryChapterRecord,
        warnings: MutableList<String>,
    ): String {
        chapter.content?.ref?.let { contentRef ->
            val text = contentStore.read(contentRef)
            if (text.isNotBlank()) return text
        }
        repository.getStoryDraftCurrentByChapterId(chapter.id)?.let { current ->
            val text = contentStore.read(current.contentRef)
            if (text.isNotBlank()) {
                warnings += "Chapter ${chapter.number} '${chapter.title}' exported from current draft because approved content ref was unavailable."
                return text
            }
        }
        warnings += "Chapter ${chapter.number} '${chapter.title}' has no available content."
        return ""
    }

    private fun ensureVolumeFolder(storyRoot: Path, volume: StoryVolumeRecord): Path {
        val folder = storyRoot.resolve("Vol_${volume.number.twoDigits()}_${sanitizeName(volume.title)}")
        Files.createDirectories(folder)
        return folder
    }

    private fun ensureChapterFolder(
        storyRoot: Path,
        volume: StoryVolumeRecord,
        chapter: StoryChapterRecord,
    ): Path {
        val volumeFolder = ensureVolumeFolder(storyRoot, volume)
        val folder = volumeFolder.resolve("Ch_${chapter.number.twoDigits()}_${sanitizeName(chapter.title)}")
        Files.createDirectories(folder)
        return folder
    }

    private fun writeFile(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(
            path,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    private fun sanitizeName(value: String): String {
        val cleaned = value
            .trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "")
            .trim('_')
        return if (cleaned.isBlank()) "untitled" else cleaned
    }

    private fun Long.twoDigits(): String = toString().padStart(2, '0')
}

