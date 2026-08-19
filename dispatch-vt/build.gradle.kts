import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * `dispatch-vt` — a terminal emulator model used to test what the screen actually showed.
 *
 * Dispatch's other test suites assert on the ANSI byte stream. Flicker is not a property of the
 * byte stream: it is a property of the sequence of *visible screen states* that stream produces.
 * This module supplies the missing piece — a VT screen model, presentation-point segmentation, and
 * the render invariants — so a test can say "the screen was blank here" instead of "the bytes
 * contained ESC[2J".
 *
 * This module deliberately bypasses `dispatch.kotlin-library`: it is a test harness, it must NOT be
 * published, and it must NOT be ABI-validated. It mirrors only the JDK-21 toolchain.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    // CI sets this after the PTY suite so an empty corpus fails instead of passing vacuously.
    systemProperty(
        "dispatch.vt.requireCorpus",
        providers.systemProperty("dispatch.vt.requireCorpus").getOrElse("false"),
    )
    // Diagnostic: -Ddispatch.vt.dump=<scenario> prints that scenario's final screen.
    systemProperty("dispatch.vt.dump", providers.systemProperty("dispatch.vt.dump").getOrElse(""))
    // The corpus replay tests read recorded PTY captures from dispatch-sample's report directory.
    systemProperty(
        "dispatch.vt.corpusDir",
        rootProject
            .layout
            .projectDirectory
            .dir("dispatch-sample/build/reports/terminal-reliability")
            .asFile
            .absolutePath,
    )
}
