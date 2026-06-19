plugins {
    id("dispatch.kotlin-library-conventions")
    id("dispatch.kotlin-serialization")
}

dependencies {
    api(projects.dispatchRuntime)
    api(projects.dispatchLayout)
    api(projects.dispatchViewmodel)
    api(projects.dispatchLifecycle)

    testImplementation(projects.dispatchWidgets)
}
