plugins {
    id("dispatch.kotlin-application")
    id("dispatch.kotlin-serialization")
}

application {
    mainClass.set("com.ead.dispatch.sample.MainKt")
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
        excludeTags("terminal-e2e")
    }
}

val terminalE2eTest by tasks.registering(Test::class) {
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
}

tasks.check {
    dependsOn(terminalE2eTest)
}
