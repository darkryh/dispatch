plugins {
    id("dispatch.kotlin-application")
    id("dispatch.kotlin-serialization")
}

application {
    mainClass.set("io.github.darkryh.dispatch.sample.MainKt")
    applicationDefaultJvmArgs =
        listOf(
            "-Dfile.encoding=utf-8",
        )
}

dependencies {
    implementation(projects.dispatchCore)
    implementation(projects.dispatchRuntime)
    implementation(projects.dispatchLayout)
    implementation(projects.dispatchWidgets)
    implementation(projects.dispatchViewmodel)
    implementation(projects.dispatchLifecycle)
    implementation(projects.dispatchNavigation)
    implementation(projects.dispatchKoin)
    implementation(libs.bundles.mordant)
    implementation(libs.coroutines.core)

    // Prevent SLF4J's "no providers" warnings from printing to stderr and corrupting the TUI.
    runtimeOnly(libs.slf4j.nop)

    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("terminal-e2e", "terminal-stress")
    }
}

val terminalReportDirectory = layout.buildDirectory.dir("reports/terminal-reliability")

val terminalE2eTest =
    tasks.register<Test>("terminalE2eTest") {
        description = "Runs the installed sample application through a real pseudo-terminal"
        group = LifecycleBasePlugin.VERIFICATION_GROUP

        testClassesDirs =
            sourceSets.test
                .get()
                .output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        dependsOn(tasks.installDist)
        shouldRunAfter(tasks.test)
        maxParallelForks = 1

        useJUnitPlatform {
            includeTags("terminal-e2e")
        }

        systemProperty(
            "dispatch.sample.binary",
            layout.buildDirectory
                .file("install/dispatch-sample/bin/dispatch-sample")
                .get()
                .asFile.absolutePath,
        )
        systemProperty("dispatch.sample.reportDir", terminalReportDirectory.get().asFile.absolutePath)
    }

val terminalStressTest =
    tasks.register<Test>("terminalStressTest") {
        description = "Runs the full installed-sample PTY stress, memory, and render reliability workflow"
        group = LifecycleBasePlugin.VERIFICATION_GROUP

        testClassesDirs =
            sourceSets.test
                .get()
                .output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        dependsOn(tasks.installDist)
        shouldRunAfter(terminalE2eTest)
        maxParallelForks = 1

        useJUnitPlatform {
            includeTags("terminal-stress")
        }

        systemProperty(
            "dispatch.sample.binary",
            layout.buildDirectory
                .file("install/dispatch-sample/bin/dispatch-sample")
                .get()
                .asFile.absolutePath,
        )
        systemProperty("dispatch.sample.reportDir", terminalReportDirectory.get().asFile.absolutePath)
    }

val terminalDiagnosticsReport =
    tasks.register("terminalDiagnosticsReport") {
        description = "Builds an aggregate index for local terminal reliability reports"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(terminalE2eTest, terminalStressTest)
        val reportDirectory = terminalReportDirectory
        outputs.file(reportDirectory.map { it.file("index.txt") })
        outputs.upToDateWhen { false }
        doLast {
            val root = reportDirectory.get().asFile
            root.mkdirs()
            val summaries =
                root
                    .walkTopDown()
                    .filter { it.name == "summary.txt" }
                    .sortedBy { it.path }
                    .toList()
            root.resolve("index.txt").writeText(
                buildString {
                    appendLine("Dispatch terminal reliability report")
                    appendLine("scenarios=${summaries.size}")
                    summaries.forEach { summary ->
                        appendLine()
                        appendLine("[${summary.parentFile.name}]")
                        append(summary.readText())
                    }
                },
            )
        }
    }

// The PTY end-to-end suite is slow and needs a real pseudo-terminal, so it is NOT wired into the
// default `check` lifecycle (that keeps `./gradlew check` fast and CI-safe everywhere). CI runs it as
// its own dedicated job, and you can opt in locally with `-PrunTerminalE2e`.
if (providers.gradleProperty("runTerminalE2e").isPresent) {
    tasks.check {
        dependsOn(terminalE2eTest)
    }
}
