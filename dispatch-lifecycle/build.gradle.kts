plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    api(libs.coroutines.core)
    api(projects.dispatchRuntime)
}
