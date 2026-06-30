/**
 * Convention plugin for Maven publishing.
 *
 * Provides:
 * - Maven Central publishing configuration
 * - Dokka documentation generation
 * - POM metadata configuration
 */
plugins {
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = project.name,
        version = project.version.toString()
    )

    pom {
        name.set(project.name)
        description.set("Dispatch - A declarative terminal UI framework for Kotlin")
        url.set("https://github.com/darkryh/dispatch")
        inceptionYear.set("2024")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("darkryh")
                name.set("Darkryh")
                url.set("https://github.com/darkryh")
            }
        }

        scm {
            url.set("https://github.com/darkryh/dispatch")
            connection.set("scm:git:git://github.com/darkryh/dispatch.git")
            developerConnection.set("scm:git:ssh://git@github.com/darkryh/dispatch.git")
        }
    }
}

dokka {
    dokkaPublications.configureEach {
        suppressInheritedMembers.set(true)
    }
    dokkaSourceSets {
        configureEach {
            skipDeprecated.set(true)
        }
    }
}

// Gradle 9 fails the build when a task consumes another task's output without a declared dependency.
// vanniktech attaches the Dokka javadoc (and sources) JARs as publication artifacts, so the Maven
// module-metadata generator reads those JARs — but the plugin never wires that edge. Declare it
// explicitly here (covers every module via the kotlin-library convention) so publishing tasks run
// after the JARs they package and the implicit-dependency validation passes.
tasks.withType<org.gradle.api.publish.tasks.GenerateModuleMetadata>().configureEach {
    dependsOn(tasks.withType<org.gradle.jvm.tasks.Jar>())
}
