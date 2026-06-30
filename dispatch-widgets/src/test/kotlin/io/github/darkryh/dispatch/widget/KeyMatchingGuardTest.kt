package io.github.darkryh.dispatch.widget

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Structural lock for the type-safe key system.
 *
 * Keyboard events must be matched against [io.github.darkryh.dispatch.input.Key] values, never by comparing
 * the raw Mordant `KeyboardEvent.key` string to a literal. This guard fails the build if the
 * `.key == "…"` / `.key != "…"` anti-pattern reappears in any production source, so the migration
 * off string matching cannot silently regress.
 *
 * The three controlled bridge files that legitimately touch raw key strings are allow-listed:
 * `Key.kt` (the single W3C-string → `Key` mapping), `KeyEventTextParser.kt` (printable-text
 * extraction), and `ExitKeyBinding.kt` (user-facing exit-key config, matched case-insensitively).
 */
class KeyMatchingGuardTest {
    private val allowList = setOf("Key.kt", "KeyEventTextParser.kt", "ExitKeyBinding.kt")
    private val antiPattern = Regex("""\.key\s*[!=]=\s*"""")

    @Test
    fun `no raw key string comparisons in production sources`() {
        val repoRoot = Path.of("..").toAbsolutePath().normalize()
        val offenders = mutableListOf<String>()

        Files.walk(repoRoot).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "kt" }
                .filter { path ->
                    val p = path.toString()
                    "/src/main/kotlin/" in p && "/build/" !in p && path.name !in allowList
                }.forEach { file ->
                    val text = Files.readString(file)
                    if (antiPattern.containsMatchIn(text)) {
                        offenders += repoRoot.relativize(file).toString()
                    }
                }
        }

        assertTrue(
            offenders.isEmpty(),
            "Raw key-string comparison found (use io.github.darkryh.dispatch.input.Key instead): " +
                offenders.joinToString(),
        )
    }
}
