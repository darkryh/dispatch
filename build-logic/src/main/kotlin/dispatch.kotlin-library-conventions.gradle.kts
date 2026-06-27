/**
 * Composite convention plugin for library modules.
 *
 * Applies:
 * - dispatch.kotlin-library (base Kotlin JVM setup)
 * - dispatch.test-conventions (testing with Kover)
 * - dispatch.quality-conventions (Detekt + Ktlint)
 * - dispatch.publishing-conventions (Maven publishing)
 *
 * This is the standard plugin for all publishable library modules.
 */
plugins {
    id("dispatch.kotlin-library")
    id("dispatch.test-conventions")
    id("dispatch.publishing-conventions")
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
