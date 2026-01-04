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
    id("org.jetbrains.kotlinx.kover")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

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

kover {
    reports {
        filters {
            excludes {
                classes("*Test", "*Test$*", "*Spec", "*Spec$*")
            }
        }
    }
}
