plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    api(projects.dispatchRuntime)
    api(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}
