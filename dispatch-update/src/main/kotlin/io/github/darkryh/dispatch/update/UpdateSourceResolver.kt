package io.github.darkryh.dispatch.update

import java.io.File

interface UpdateSourceResolver {
    fun resolve(appName: String, config: UpdateConfig, env: UpdateEnvironment): UpdateSource
}

class DefaultUpdateSourceResolver : UpdateSourceResolver {
    override fun resolve(appName: String, config: UpdateConfig, env: UpdateEnvironment): UpdateSource {
        config.sourceOverride?.let { return it }

        return when (env.os) {
            OperatingSystem.MAC -> if (commandExists("brew", env)) UpdateSource.HOMEBREW else UpdateSource.MANUAL
            OperatingSystem.WINDOWS -> if (commandExists("scoop", env)) UpdateSource.SCOOP else UpdateSource.MANUAL
            OperatingSystem.LINUX -> if (commandExists("apt", env)) UpdateSource.APT else UpdateSource.MANUAL
            OperatingSystem.OTHER -> UpdateSource.UNKNOWN
        }
    }

    private fun commandExists(command: String, env: UpdateEnvironment): Boolean {
        val extensions = when (env.os) {
            OperatingSystem.WINDOWS -> listOf(".exe", ".cmd", ".bat", "")
            else -> listOf("")
        }
        for (path in env.pathEntries) {
            for (ext in extensions) {
                val candidate = File(path, command + ext)
                if (env.fileExists(candidate.path)) {
                    return true
                }
            }
        }
        return false
    }
}
