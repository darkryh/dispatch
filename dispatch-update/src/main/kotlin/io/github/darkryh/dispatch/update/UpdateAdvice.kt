package io.github.darkryh.dispatch.update

data class UpdateAdvice(
    val currentVersion: String,
    val latestVersion: String,
    val source: UpdateSource,
    val command: String?,
    val message: String,
)
