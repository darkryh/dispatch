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
        // Match the project's JDK 21 toolchain so detekt analyzes against the same bytecode target.
        jvmTarget = "21"
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
                // Dispatch uses Compose-style PascalCase names for @Composable UI functions (and the
                // framework's own @Dispatchable/@DispatchRenderer markers). Exempt them from camelCase.
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable,Dispatchable,DispatchRenderer",
                // Allow on-demand (wildcard) imports for DSL/runtime packages where it is conventional.
                "ij_kotlin_packages_to_use_import_on_demand" to
                    "java.util.*,kotlinx.coroutines.*,kotlinx.coroutines.flow.*," +
                    "androidx.compose.runtime.*,io.github.darkryh.dispatch.layout.*",
                // File names intentionally group related declarations (e.g. DispatchUi.kt); not enforced.
                "ktlint_standard_filename" to "disabled",
                // `_state`-style backing properties are idiomatic; ktlint's stricter check is off.
                "ktlint_standard_backing-property-naming" to "disabled",
                // ktlint's own formatter wraps multi-annotation function types (@DispatchRenderer
                // @Composable () -> Unit), which then trips this spacing rule — a self-conflict; off.
                "ktlint_standard_function-type-modifier-spacing" to "disabled",
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
