plugins {
    id("dispatch.kotlin-library-conventions")
    id("dispatch.kotlin-serialization")
}

dependencies {
    api(projects.dispatchCore)

    testImplementation(projects.dispatchLayout)
    testImplementation(projects.dispatchWidgets)
    testImplementation(projects.dispatchRenderer)
    testImplementation(projects.dispatchNavigation)
}
