package io.github.darkryh.dispatch.update

class AptUpdateProvider(
    private val packageName: String,
    private val runner: CommandRunner,
) : CommandBasedUpdateProvider {
    override suspend fun latestVersion(): String? {
        val result = runner.run("apt-cache", "policy", packageName)
        if (!result.isSuccess) return null
        return parseCandidate(result.stdout)
    }

    private fun parseCandidate(output: String): String? {
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("Candidate:")) continue
            val candidate = trimmed.removePrefix("Candidate:").trim()
            return candidate.takeIf { it.isNotBlank() && it != "(none)" }
        }
        return null
    }
}
