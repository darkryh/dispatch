/**
 * Convention plugin for Kotlin serialization support.
 *
 * Provides:
 * - Kotlin serialization plugin
 * - kotlinx-serialization-json dependency
 */
plugins {
    kotlin("plugin.serialization")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    "implementation"(libs.kotlinx.serialization.json)
}
