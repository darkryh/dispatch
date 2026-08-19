plugins {
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.binary.compatibility)
}

// Public-API surface guard. `apiCheck` auto-wires into `check` (and thus `validateAll`); run
// `./gradlew apiDump` to (re)generate the committed <module>/api/<module>.api baselines whenever a
// public ABI change is intended. Non-published projects are excluded.
apiValidation {
    ignoredProjects += "dispatch-sample"
    ignoredProjects += "dispatch-benchmarks"
    // dispatch-vt is the render-invariant test harness (a terminal model + the flicker analyzer).
    // It is never published, so it has no public ABI to guard.
    ignoredProjects += "dispatch-vt"
}

allprojects {
    // Coordinates confirmed for Maven Central: the group is the project owner's own verified
    // namespace (the earlier "com.github.ajalt.mordant.dispatch" was the Mordant author's and could
    // not be signed/published). First public release is the 1.0.0 beta line.
    group = "io.github.darkryh.dispatch"
    // Version is supplied by the release pipeline via -PVERSION_NAME=<git tag without the leading 'v'>.
    // Local/dev builds default to a SNAPSHOT so they never collide with a published release.
    version = providers.gradleProperty("VERSION_NAME").getOrElse("1.0.0-beta04-SNAPSHOT")

    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(":") }
val koverRequested =
    requestedTaskNames.any {
        it == "validateAll" || it.startsWith("kover")
    }

if (koverRequested) {
    apply(plugin = libs.plugins.kover.get().pluginId)

    // Aggregate Kover reports from all library modules (excluding sample)
    dependencies {
        subprojects
            .filter { it.name != "dispatch-sample" && it.name != "dispatch-benchmarks" }
            .forEach {
                add("kover", dependencies.project(":${it.name}"))
            }
    }
}

// Aggregate quality check task
tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs all quality checks (detekt + ktlint) across all modules"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("detekt") })
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("ktlintCheck") })
}

// Aggregate formatting task
tasks.register("formatAll") {
    group = "formatting"
    description = "Runs ktlint format across all modules"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("ktlintFormat") })
}

// Complete validation task
tasks.register("validateAll") {
    group = "verification"
    description = "Runs all checks including build, test, and quality"
    dependsOn("check", "qualityCheck", "koverHtmlReport", "verifyModuleBoundaries")
}

tasks.register("verifyModuleBoundaries") {
    group = "verification"
    description = "Validates forbidden project dependency edges between Dispatch modules"

    doLast {
        val forbiddenEdges =
            mapOf(
                ":dispatch-navigation" to setOf(":dispatch-renderer", ":dispatch-core"),
                ":dispatch-widgets" to setOf(":dispatch-core"),
                ":dispatch-renderer" to setOf(":dispatch-core"),
            )

        val violations = mutableListOf<String>()
        forbiddenEdges.forEach { (projectPath, forbiddenTargets) ->
            val project = project(projectPath)
            val directProjectDeps = mutableSetOf<String>()
            project.configurations
                .matching { it.name in setOf("api", "implementation") }
                .forEach { config ->
                    config.dependencies.forEach { dependency ->
                        if (dependency is org.gradle.api.artifacts.ProjectDependency) {
                            directProjectDeps += dependency.path
                        }
                    }
                }

            val invalid = directProjectDeps.intersect(forbiddenTargets)
            if (invalid.isNotEmpty()) {
                violations += "$projectPath has forbidden deps: ${invalid.sorted().joinToString()}"
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Module boundary violations detected:")
                    violations.sorted().forEach { appendLine("- $it") }
                }
            )
        }
    }
}
