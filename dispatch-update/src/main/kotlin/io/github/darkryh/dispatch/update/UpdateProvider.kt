package io.github.darkryh.dispatch.update

interface UpdateProvider {
    suspend fun latestVersion(): String?
}

interface CommandBasedUpdateProvider : UpdateProvider

class StaticUpdateProvider(
    private val version: String?,
) : UpdateProvider {
    override suspend fun latestVersion(): String? = version
}
