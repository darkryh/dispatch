package io.github.darkryh.dispatch.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BrewUpdateProvider(
    private val formula: String,
    private val runner: CommandRunner,
) : CommandBasedUpdateProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun latestVersion(): String? {
        val result = runner.run("brew", "info", "--json=v2", formula)
        if (!result.isSuccess) return null

        val response =
            runCatching { json.decodeFromString<BrewInfoResponse>(result.stdout) }.getOrNull()
                ?: return null
        val entry = response.formulae.firstOrNull() ?: return null
        return entry.versions.stable
    }

    @Serializable
    private data class BrewInfoResponse(
        val formulae: List<BrewFormula> = emptyList(),
    )

    @Serializable
    private data class BrewFormula(
        val name: String,
        val versions: BrewVersions,
    )

    @Serializable
    private data class BrewVersions(
        val stable: String? = null,
    )
}
