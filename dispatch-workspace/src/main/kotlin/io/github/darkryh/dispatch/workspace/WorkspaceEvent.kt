package io.github.darkryh.dispatch.workspace

import java.nio.file.Path

enum class WorkspaceEventType {
    CREATED,
    MODIFIED,
    DELETED,
    OVERFLOW,
}

data class WorkspaceEvent(
    val path: Path,
    val type: WorkspaceEventType,
    val timestamp: Long,
    val hash: String? = null,
)
