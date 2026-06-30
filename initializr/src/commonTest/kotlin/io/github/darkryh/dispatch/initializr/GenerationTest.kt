package io.github.darkryh.dispatch.initializr

import io.github.darkryh.dispatch.initializr.generate.ProjectGenerator
import io.github.darkryh.dispatch.initializr.model.ProjectConfig
import io.github.darkryh.dispatch.initializr.model.ProjectConfigValidator
import io.github.darkryh.dispatch.initializr.template.StarterTemplate
import io.github.darkryh.dispatch.initializr.zip.Base64
import io.github.darkryh.dispatch.initializr.zip.Crc32
import io.github.darkryh.dispatch.initializr.zip.ZipArchive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerationTest {
    private val config =
        ProjectConfig(
            projectName = "Acme TUI",
            groupId = "com.acme",
            artifactId = "acme-tui",
            packageName = "com.acme.tui",
            appVersion = "0.2.0",
        )

    @Test
    fun defaultConfigIsValid() {
        assertTrue(ProjectConfigValidator.isValid(ProjectConfig.DEFAULT))
    }

    @Test
    fun rejectsBadPackageAndArtifact() {
        val bad =
            config.copy(packageName = "com.1bad.pkg", artifactId = "Bad_Artifact", groupId = "Com.Acme")
        val fields = ProjectConfigValidator.validate(bad).map { it.field }.toSet()
        assertTrue(fields.size >= 3, "expected package, artifact and group errors, got $fields")
    }

    @Test
    fun substitutesEveryPlaceholder() {
        val files = ProjectGenerator.generate(config)
        for (file in files) {
            assertFalse(file.path.contains("{{"), "unsubstituted token in path: ${file.path}")
            assertFalse(file.content.contains("{{"), "unsubstituted token in ${file.path}")
        }
    }

    @Test
    fun placesSourcesUnderPackagePath() {
        val files = ProjectGenerator.generate(config)
        val paths = files.map { it.path }
        assertTrue("src/main/kotlin/com/acme/tui/Main.kt" in paths, "Main.kt not at package path: $paths")
        assertTrue("src/main/kotlin/com/acme/tui/di/AppModule.kt" in paths)
    }

    @Test
    fun buildFileReferencesMavenCentralCoordinates() {
        val build = ProjectGenerator.generate(config).single { it.path == "build.gradle.kts" }.content
        assertTrue(
            build.contains("io.github.darkryh:dispatch-widgets:${StarterTemplate.DISPATCH_VERSION}"),
            "build.gradle.kts missing published dispatch-widgets coordinate",
        )
        assertTrue(build.contains("kotlin(\"jvm\") version \"${StarterTemplate.KOTLIN_VERSION}\""))
    }

    @Test
    fun dollarMarkerBecomesKotlinTemplate() {
        val screen = ProjectGenerator.generate(config).single { it.path.endsWith("MainScreen.kt") }.content
        assertTrue(screen.contains("\${state.count}"), "the {{D}} marker should yield a \$ template")
        assertFalse(screen.contains("{{D}}"))
    }

    @Test
    fun settingsUsesArtifactId() {
        val settings = ProjectGenerator.generate(config).single { it.path == "settings.gradle.kts" }.content
        assertTrue(settings.contains("rootProject.name = \"acme-tui\""))
    }

    @Test
    fun emitsExecutableRunScript() {
        val runSh = ProjectGenerator.generate(config).single { it.path == "run.sh" }
        assertTrue(runSh.executable, "run.sh should be marked executable")
        assertTrue(runSh.content.startsWith("#!/usr/bin/env bash"), "run.sh missing shebang")
        assertTrue(runSh.content.contains("installDist"), "run.sh should build via installDist")
        assertTrue(runSh.content.contains("APP_NAME=\"acme-tui\""), "run.sh should bind APP_NAME to the artifact id")
        assertTrue(
            runSh.content.contains("build/install/\$APP_NAME/bin/\$APP_NAME"),
            "run.sh wrong launcher path",
        )
        assertFalse(runSh.content.contains("{{"), "run.sh has unsubstituted tokens")
    }

    @Test
    fun zipRecordsUnixExecutableMode() {
        val files = ProjectGenerator.generate(config)
        val zip = ZipArchive.create(files)

        // Walk the central directory and check the external-attrs high 16 bits carry the Unix mode:
        // 0o100755 for run.sh, 0o100644 for everything else.
        val cdSig = byteArrayOf(0x50, 0x4B, 0x01, 0x02)
        var seenExecutable = false
        var i = 0
        while (i <= zip.size - 4) {
            if (zip[i] == cdSig[0] && zip[i + 1] == cdSig[1] && zip[i + 2] == cdSig[2] && zip[i + 3] == cdSig[3]) {
                val nameLen = (zip[i + 28].toInt() and 0xFF) or ((zip[i + 29].toInt() and 0xFF) shl 8)
                val mode = (zip[i + 40].toInt() and 0xFF) or ((zip[i + 41].toInt() and 0xFF) shl 8)
                val name = zip.copyOfRange(i + 46, i + 46 + nameLen).decodeToString()
                if (name == "run.sh") {
                    assertEquals(0x81ED, mode, "run.sh should be mode 0o100755")
                    seenExecutable = true
                } else {
                    assertEquals(0x81A4, mode, "$name should be mode 0o100644")
                }
            }
            i++
        }
        assertTrue(seenExecutable, "run.sh entry not found in central directory")
    }

    @Test
    fun crc32MatchesKnownVector() {
        // CRC-32 of the ASCII string "123456789" is the well-known check value 0xCBF43926.
        assertEquals(0xCBF43926L, Crc32.compute("123456789".encodeToByteArray()))
    }

    @Test
    fun base64MatchesKnownVectors() {
        assertEquals("TWFu", Base64.encode("Man".encodeToByteArray()))
        assertEquals("TWE=", Base64.encode("Ma".encodeToByteArray()))
        assertEquals("TQ==", Base64.encode("M".encodeToByteArray()))
    }

    @Test
    fun zipHasValidSignatureAndEntryCount() {
        val files = ProjectGenerator.generate(config)
        val zip = ZipArchive.create(files)

        // Local file header signature 0x04034B50, little-endian: PK
        assertEquals(0x50, zip[0].toInt() and 0xFF)
        assertEquals(0x4B, zip[1].toInt() and 0xFF)
        assertEquals(0x03, zip[2].toInt() and 0xFF)
        assertEquals(0x04, zip[3].toInt() and 0xFF)

        // End-of-central-directory total-entries field equals the number of files.
        val eocdSig = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
        val eocd = lastIndexOf(zip, eocdSig)
        assertTrue(eocd >= 0, "no EOCD record found")
        val totalEntries = (zip[eocd + 10].toInt() and 0xFF) or ((zip[eocd + 11].toInt() and 0xFF) shl 8)
        assertEquals(files.size, totalEntries)
    }

    private fun lastIndexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in haystack.size - needle.size downTo 0) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
