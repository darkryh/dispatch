plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    // kotlinx-coroutines-core is inherited transitively from dispatch-runtime
    api(projects.dispatchRuntime)
}
