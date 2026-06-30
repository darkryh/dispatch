package io.github.darkryh.dispatch.update

class ScoopUpdateProvider(
    private val app: String,
    private val runner: CommandRunner,
    private val refreshBeforeCheck: Boolean = false,
) : CommandBasedUpdateProvider {
    override suspend fun latestVersion(): String? {
        if (refreshBeforeCheck) {
            val refresh = runner.run("scoop", "update")
            if (!refresh.isSuccess) return null
        }

        val result = runner.run("scoop", "status")
        if (!result.isSuccess) return null
        return parseLatest(result.stdout)
    }

    private fun parseLatest(output: String): String? {
        val lines = output.lines()
        if (lines.any { it.contains("No outdated apps", ignoreCase = true) }) return null

        return lines
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("Name", ignoreCase = true) }
            .filterNot { it.startsWith("---") }
            .filterNot { it.contains("Installed Version", ignoreCase = true) }
            .map { line -> line.split(WHITESPACE).filter { it.isNotBlank() } }
            .filter { it.size >= 3 }
            .firstOrNull { it[0].equals(app, ignoreCase = true) }
            ?.get(2)
    }

    private companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}
