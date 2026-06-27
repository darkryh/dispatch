/**
 * Convention plugin for test configuration.
 *
 * Provides:
 * - JUnit5 test platform
 * - Kover test coverage
 * - Common test dependencies
 * - Parallel test execution
 */
plugins {
    kotlin("jvm")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()
val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(":") }
val koverRequested =
    requestedTaskNames.any {
        it == "validateAll" || it.startsWith("kover")
    }

dependencies {
    "testImplementation"(kotlin("test"))
    "testImplementation"(libs.kotest)
    "testImplementation"(libs.coroutines.test)
    "testImplementation"(libs.junit.jupiter)
    "testRuntimeOnly"(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

if (koverRequested) {
    apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension>("kover") {
        reports {
            filters {
                excludes {
                    classes("*Test", "*Test$*", "*Spec", "*Spec$*")
                }
            }
        }
    }
}
