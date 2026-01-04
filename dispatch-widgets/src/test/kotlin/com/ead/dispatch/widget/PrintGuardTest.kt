package com.ead.dispatch.widget

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards against accidental println/printLines in widget code (composables should render, not print).
 */
class PrintGuardTest {
    @Test
    fun `widgets do not call println or printLines`() {
        val root = Path.of("src/main/kotlin")
        val forbidden = listOf("println(", "printLines(")
        val offenders = mutableListOf<String>()

        Files.walk(root).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.forEach { file ->
                val text = Files.readString(file)
                if (forbidden.any { it in text }) {
                    offenders += file.toString()
                }
            }
        }

        assertTrue(offenders.isEmpty(), "Forbidden print calls found in widget sources: ${offenders.joinToString()}")
    }
}
