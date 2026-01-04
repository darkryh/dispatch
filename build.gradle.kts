plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

allprojects {
    group = "com.github.ajalt.mordant.dispatch"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

// Aggregate Kover reports from all library modules (excluding sample)
dependencies {
    subprojects.filter { it.name != "dispatch-sample" }.forEach {
        kover(project(":${it.name}"))
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
    dependsOn("check", "qualityCheck", "koverHtmlReport")
}
