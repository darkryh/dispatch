/**
 * Base convention plugin for Kotlin JVM library modules.
 *
 * Provides:
 * - Kotlin JVM plugin configuration
 * - Java toolchain (JVM 21)
 * - Compiler options and warnings
 * - Sources and Javadoc JARs
 */
plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    `java-library`
}

val versionCatalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(versionCatalog.findLibrary("compose-runtime").get())
    testCompileOnly(versionCatalog.findLibrary("compose-runtime").get())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        allWarningsAsErrors.set(false)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
