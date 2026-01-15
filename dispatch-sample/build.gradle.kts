plugins {
    id("dispatch.kotlin-application")
    id("dispatch.kotlin-serialization")
    id("app.cash.sqldelight") version "2.2.1"
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

    implementation("com.russhwolf:multiplatform-settings:1.3.0")
    implementation("com.russhwolf:multiplatform-settings-serialization:1.3.0")
    implementation("com.russhwolf:multiplatform-settings-coroutines:1.3.0")
    implementation("com.russhwolf:multiplatform-settings-make-observable:1.3.0")


    implementation("net.harawata:appdirs:1.5.0")
    implementation(libs.bundles.koog)
    implementation("app.cash.sqldelight:sqlite-driver:2.2.1")
    implementation("app.cash.sqldelight:coroutines-extensions:2.2.1")

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
