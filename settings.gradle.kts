pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "dispatch"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":dispatch-core")
include(":dispatch-runtime")
include(":dispatch-renderer")
include(":dispatch-layout")
include(":dispatch-widgets")
include(":dispatch-navigation")
include(":dispatch-viewmodel")
include(":dispatch-lifecycle")
include(":dispatch-koin")
include(":dispatch-update")
include(":dispatch-update-brew")
include(":dispatch-update-scoop")
include(":dispatch-update-apt")
include(":dispatch-update-github")
include(":dispatch-workspace")
include(":dispatch-sample")
include(":koog-context-orchestrator")
