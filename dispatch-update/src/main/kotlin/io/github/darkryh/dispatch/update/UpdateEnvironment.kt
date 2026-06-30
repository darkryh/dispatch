package io.github.darkryh.dispatch.update

import java.io.File

enum class OperatingSystem {
    MAC,
    WINDOWS,
    LINUX,
    OTHER,
}

interface UpdateEnvironment {
    val os: OperatingSystem
    val pathEntries: List<String>
    val classPathEntries: List<String>

    fun env(name: String): String?

    fun fileExists(path: String): Boolean
}

class SystemUpdateEnvironment : UpdateEnvironment {
    override val os: OperatingSystem = detectOs()
    override val pathEntries: List<String> =
        System
            .getenv("PATH")
            ?.split(File.pathSeparator)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    override val classPathEntries: List<String> =
        System
            .getProperty("java.class.path")
            ?.split(File.pathSeparator)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    override fun env(name: String): String? = System.getenv(name)

    override fun fileExists(path: String): Boolean = File(path).exists()

    private fun detectOs(): OperatingSystem {
        val name = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            name.contains("mac") || name.contains("darwin") -> OperatingSystem.MAC
            name.contains("win") -> OperatingSystem.WINDOWS
            name.contains("nix") || name.contains("nux") || name.contains("linux") -> OperatingSystem.LINUX
            else -> OperatingSystem.OTHER
        }
    }
}
