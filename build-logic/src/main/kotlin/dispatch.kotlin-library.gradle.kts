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
    // NOTE: do NOT call withSourcesJar()/withJavadocJar() here. The vanniktech maven-publish plugin
    // (applied via dispatch.publishing-conventions) already attaches a sources JAR and a Dokka-based
    // javadoc JAR to each publication; adding them again produces two artifacts with the same
    // 'javadoc'/'sources' classifier and fails publishing with "multiple artifacts with the identical
    // extension and classifier".
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
