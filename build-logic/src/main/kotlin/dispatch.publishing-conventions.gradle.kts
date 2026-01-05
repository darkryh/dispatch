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

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    dokkaSourceSets {
        configureEach {
            suppressInheritedMembers.set(true)
            skipDeprecated.set(true)
        }
    }
}
