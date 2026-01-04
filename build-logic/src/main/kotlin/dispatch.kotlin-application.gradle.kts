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
    id("dispatch.quality-conventions")
    application
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
