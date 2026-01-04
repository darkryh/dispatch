plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    // kotlinx-coroutines-core and mordant are inherited transitively from dispatch-runtime
    api(projects.dispatchRuntime)
}
