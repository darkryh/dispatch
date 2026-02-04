package com.ead.dispatch.workspace

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Configuration for watching a workspace on disk.
 */
data class WorkspaceWatchConfig(
    val root: Path,
    val recursive: Boolean = true,
    val includeGlobs: List<String> = listOf("**"),
    val excludeGlobs: List<String> = listOf(".git/**", "build/**", "**/.gradle/**"),
    val debounce: Duration = 250.milliseconds,
    val hashing: HashingMode = HashingMode.NONE,
    val hashAlgorithm: String = "SHA-256",
    val followSymlinks: Boolean = false,
    val maxEventBatchSize: Int = 512,
)
