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
    id("dispatch.quality-conventions")
    id("dispatch.publishing-conventions")
}
