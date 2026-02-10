/**
 * Convention plugin for code quality tools.
 *
 * Provides:
 * - Detekt for static analysis
 * - Ktlint for code formatting
 * - Report generation (HTML, XML, SARIF)
 */
plugins {
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    // Detekt 1.23.x currently supports JVM targets up to 22.
    jvmTarget = "22"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        txt.required.set(false)
    }
}

ktlint {
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    additionalEditorconfig.set(
        mapOf(
            // Dispatch uses Compose-style PascalCase names for @Dispatchable UI functions.
            "ktlint_function_naming_ignore_when_annotated_with" to "Dispatchable,DispatchRenderer",
        ),
    )

    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

// Integrate quality checks with the check task
tasks.named("check") {
    dependsOn(tasks.withType<io.gitlab.arturbosch.detekt.Detekt>())
    dependsOn("ktlintCheck")
}
