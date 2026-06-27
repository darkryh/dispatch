/**
 * Convention plugin for code quality tools.
 *
 * Provides:
 * - Detekt for static analysis
 * - Ktlint for code formatting
 * - Report generation (HTML, XML, SARIF)
 */
val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(":") }
val aggregateQualityRequested =
    requestedTaskNames.any { it in setOf("check", "qualityCheck", "validateAll") }
val detektRequested =
    aggregateQualityRequested || requestedTaskNames.any { it.startsWith("detekt") }
val ktlintRequested =
    aggregateQualityRequested ||
        requestedTaskNames.any {
            it == "formatAll" || it.startsWith("ktlint")
        }

if (detektRequested) {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension>("detekt") {
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
}

if (ktlintRequested) {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension>("ktlint") {
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
}

// Integrate quality checks with the check task
if (aggregateQualityRequested) {
    tasks.named("check") {
        if (detektRequested) {
            dependsOn(tasks.withType<io.gitlab.arturbosch.detekt.Detekt>())
        }
        if (ktlintRequested) {
            dependsOn("ktlintCheck")
        }
    }
}
