package com.ead.dispatch.update

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

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("Name", ignoreCase = true)) continue
            if (trimmed.startsWith("---")) continue
            if (trimmed.contains("Installed Version", ignoreCase = true)) continue

            val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size < 3) continue
            if (!parts[0].equals(app, ignoreCase = true)) continue
            return parts[2]
        }

        return null
    }
}
