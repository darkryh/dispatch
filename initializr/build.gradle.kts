import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.4.0"
    id("org.jetbrains.compose") version "1.9.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

group = "io.github.darkryh"
version = "1.0.0-SNAPSHOT"

kotlin {
    // JVM target exists ONLY so the pure generation logic (model/template/substitution/zip — all in
    // commonMain, no browser APIs) can be unit-tested quickly with kotlin.test via `jvmTest`.
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "initializr.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // The Compose compiler plugin is applied to every target (incl. the test-only JVM target),
            // so the Compose runtime must be on the common class path even though common code is pure.
            implementation(compose.runtime)
            // Bundled JetBrains Mono fonts live in commonMain/composeResources; the generated `Res`
            // class is emitted into commonMain and consumed from the Wasm UI.
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        wasmJsMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}

// Generate the Compose `Res` accessor class (for the bundled JetBrains Mono fonts) under our package.
compose.resources {
    publicResClass = false
    packageOfResClass = "io.github.darkryh.dispatch.initializr.resources"
}
