plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    api(projects.dispatchRuntime)
    api(projects.dispatchLayout)
    api(projects.dispatchRenderer)
    api(projects.dispatchNavigation)
    api(projects.dispatchViewmodel)
    api(projects.dispatchWidgets)
    api(projects.dispatchLifecycle)
    api(libs.mordant.jvm.jna)
}
