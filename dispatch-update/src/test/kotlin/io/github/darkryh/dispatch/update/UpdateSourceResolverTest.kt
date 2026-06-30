package io.github.darkryh.dispatch.update

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateSourceResolverTest {
    @Test
    fun `detects homebrew install`() {
        val env = FakeUpdateEnvironment(
            os = OperatingSystem.MAC,
            pathEntries = listOf("/opt/homebrew/bin"),
            existingPaths = setOf("/opt/homebrew/bin/brew"),
        )

        val resolver = DefaultUpdateSourceResolver()
        val source = resolver.resolve("xtory", UpdateConfig(), env)
        assertEquals(UpdateSource.HOMEBREW, source)
    }

    @Test
    fun `detects scoop install`() {
        val env = FakeUpdateEnvironment(
            os = OperatingSystem.WINDOWS,
            pathEntries = listOf("C:\\Users\\me\\scoop\\shims"),
            existingPaths = setOf("C:\\Users\\me\\scoop\\shims\\scoop.exe"),
        )

        val resolver = DefaultUpdateSourceResolver()
        val source = resolver.resolve("xtory", UpdateConfig(), env)
        assertEquals(UpdateSource.SCOOP, source)
    }

    @Test
    fun `detects apt install`() {
        val env = FakeUpdateEnvironment(
            os = OperatingSystem.LINUX,
            pathEntries = listOf("/usr/bin"),
            existingPaths = setOf("/usr/bin/apt"),
        )

        val resolver = DefaultUpdateSourceResolver()
        val source = resolver.resolve("xtory", UpdateConfig(), env)
        assertEquals(UpdateSource.APT, source)
    }

    @Test
    fun `falls back to manual install`() {
        val env = FakeUpdateEnvironment(
            os = OperatingSystem.LINUX,
            pathEntries = listOf("/usr/bin"),
            existingPaths = emptySet(),
        )

        val resolver = DefaultUpdateSourceResolver()
        val source = resolver.resolve("xtory", UpdateConfig(), env)
        assertEquals(UpdateSource.MANUAL, source)
    }

    private class FakeUpdateEnvironment(
        override val os: OperatingSystem,
        override val pathEntries: List<String>,
        private val existingPaths: Set<String>,
    ) : UpdateEnvironment {
        override val classPathEntries: List<String> = emptyList()

        override fun env(name: String): String? = null

        override fun fileExists(path: String): Boolean {
            val normalized = normalize(path)
            return existingPaths.any { normalize(it) == normalized }
        }

        override fun toString(): String {
            return "FakeUpdateEnvironment(os=$os)"
        }

        private fun normalize(value: String): String {
            return value.replace("\\", "/")
        }
    }
}
