package com.ead.dispatch.sample.domain.content

import com.ead.dispatch.sample.domain.Pathing
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

class ChapterContentStore(
    private val root: Path = Pathing.applicationDirectory.resolve("chapter-content"),
) {
    init {
        Files.createDirectories(root)
    }

    fun read(contentRef: String): String {
        val path = resolve(contentRef)
        if (!Files.exists(path)) return ""
        return Files.readString(path)
    }

    fun write(
        storyId: String,
        chapterId: String,
        text: String,
        tag: String = "draft",
    ): String {
        val chapterDirectory = root.resolve(storyId).resolve(chapterId)
        Files.createDirectories(chapterDirectory)

        val safeTag = tag.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "${System.currentTimeMillis()}-${safeTag}-${UUID.randomUUID()}.txt"
        val finalPath = chapterDirectory.resolve(fileName)
        val tempPath = chapterDirectory.resolve("$fileName.tmp")

        Files.writeString(
            tempPath,
            text,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        Files.move(
            tempPath,
            finalPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
        return referenceOf(finalPath)
    }

    fun delete(contentRef: String) {
        val path = resolve(contentRef)
        runCatching { Files.deleteIfExists(path) }
    }

    private fun resolve(contentRef: String): Path {
        val normalized = contentRef.removePrefix("content:")
        return root.resolve(normalized).normalize()
    }

    private fun referenceOf(path: Path): String {
        val relative = root.relativize(path).toString().replace('\\', '/')
        return "content:$relative"
    }
}
