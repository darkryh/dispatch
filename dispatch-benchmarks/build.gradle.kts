import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * JMH-backed microbenchmarks for Dispatch hot paths (P0.2).
 *
 * This module deliberately bypasses the `dispatch.kotlin-library` convention stack: it must NOT be
 * published, must NOT generate sources/javadoc jars, and must NOT be ABI-validated. It only mirrors
 * the JDK-21 toolchain. JMH subclasses every `@State` class, so `kotlin-allopen` is required to open
 * them, or benchmark generation fails.
 *
 * Run:  ./gradlew :dispatch-benchmarks:benchmark
 * GC:   ./gradlew :dispatch-benchmarks:allocBenchmark   (wires JMH -prof gc)
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

// JMH generates subclasses of @State classes; allopen must open them.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

dependencies {
    implementation(libs.kotlinx.benchmark.runtime)

    implementation(projects.dispatchCore)
    implementation(projects.dispatchRuntime)
    implementation(projects.dispatchRenderer)
    implementation(projects.dispatchLayout)

    // compose-runtime is compileOnly in the libraries; LayoutNode / modifiers transitively need it.
    implementation(libs.compose.runtime)
    implementation(libs.coroutines.core)
    implementation(libs.bundles.mordant)
}

benchmark {
    targets {
        register("main")
    }
    configurations {
        named("main") {
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
        }
        // Allocation-profiling variant: fewer warmups, single fork. For per-op allocation
        // (gc.alloc.rate.norm) run JMH's gc profiler, e.g.:
        //   ./gradlew :dispatch-benchmarks:allocBenchmark -PjmhArgs="-prof gc"
        register("alloc") {
            warmups = 3
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", "1")
        }
    }
}
