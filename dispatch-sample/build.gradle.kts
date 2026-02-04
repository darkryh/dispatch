plugins {
    id("dispatch.kotlin-application")
    id("dispatch.kotlin-serialization")
    alias(libs.plugins.sqldelight)
}

application {
    mainClass.set("com.ead.dispatch.sample.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=utf-8"
    )
}

dependencies {
    implementation(projects.dispatchCore)
    implementation(projects.dispatchNavigation)
    implementation(projects.dispatchKoin)
    implementation(libs.bundles.mordant)
    implementation(libs.coroutines.core)

    implementation(libs.multiplatform.settings)
    implementation(libs.multiplatform.settings.serialization)
    implementation(libs.multiplatform.settings.coroutines)
    implementation(libs.multiplatform.settings.make.observable)

    implementation(libs.appdirs)
    implementation(libs.bundles.koog)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.sqldelight.coroutines)

    // Prevent SLF4J's "no providers" warnings from printing to stderr and corrupting the TUI.
    runtimeOnly(libs.slf4j.nop)

}

sqldelight {
    databases {
        create("DispatchDatabase") {
            packageName.set("com.ead.dispatch.sample")
        }
    }
}
