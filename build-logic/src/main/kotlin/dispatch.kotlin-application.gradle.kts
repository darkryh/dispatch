/**
 * Convention plugin for executable application modules.
 *
 * Provides:
 * - Kotlin JVM plugin
 * - Application plugin
 * - Test conventions (without publishing)
 * - JVM args configuration
 *
 * Note: Does NOT include publishing conventions since applications
 * are typically not published to Maven repositories.
 */
plugins {
    id("dispatch.kotlin-library")
    id("dispatch.test-conventions")
    application
}

val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(":") }
val qualityRequested =
    requestedTaskNames.any {
        it in setOf("check", "qualityCheck", "validateAll", "formatAll") ||
            it.startsWith("detekt") ||
            it.startsWith("ktlint")
    }

if (qualityRequested) {
    apply(plugin = "dispatch.quality-conventions")
}

// Disable publishing tasks for applications
tasks.matching { it.name.contains("publish", ignoreCase = true) }.configureEach {
    enabled = false
}

tasks.matching { it.name.contains("sign", ignoreCase = true) }.configureEach {
    enabled = false
}

application {
    applicationDefaultJvmArgs = listOf(
        "-Xmx512m",
        "-XX:+UseG1GC"
    )
}
