plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    implementation(projects.dispatchNavigation)
    api(projects.dispatchRuntime)
    api(projects.dispatchViewmodel)
    api(libs.koin.core)
}
