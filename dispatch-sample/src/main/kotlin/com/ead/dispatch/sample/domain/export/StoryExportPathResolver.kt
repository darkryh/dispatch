package com.ead.dispatch.sample.domain.export

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object StoryExportPathResolver {
    fun resolve(request: StoryExportRequest): Result<Path> = runCatching {
        val cwd = Paths.get("").toAbsolutePath().normalize()
        val basePath = when (request.outputTarget) {
            StoryExportOutputTarget.CWD -> cwd
            StoryExportOutputTarget.DOWNLOADS -> {
                val userHome = System.getProperty("user.home")
                Paths.get(userHome).resolve("Downloads").toAbsolutePath().normalize()
            }
            StoryExportOutputTarget.PATH -> {
                val raw = request.outputPath?.trim().orEmpty()
                require(raw.isNotEmpty()) { "outputPath is required when outputTarget=PATH." }
                val candidate = Paths.get(raw)
                if (candidate.isAbsolute) candidate.normalize() else cwd.resolve(candidate).normalize()
            }
        }
        Files.createDirectories(basePath)
        basePath
    }
}

