plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    implementation(projects.dispatchRuntime)
    implementation(projects.dispatchLayout)
    implementation(projects.dispatchRenderer)
    implementation(projects.dispatchNavigation)
    implementation(projects.dispatchViewmodel)
    implementation(projects.dispatchWidgets)
    implementation(projects.dispatchLifecycle)
    implementation(libs.mordant.jvm.jna)
}
