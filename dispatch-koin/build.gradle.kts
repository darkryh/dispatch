plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    implementation(projects.dispatchNavigation)
    api(projects.dispatchRuntime)
    api(projects.dispatchViewmodel)
    api(libs.koin.core)

    // Startup validation must tolerate kotlinx.serialization failures (incl. subclasses like
    // MissingFieldException) without compiling against serialization itself; tests reproduce
    // those failures with the real exception types.
    testImplementation(libs.kotlinx.serialization.json)
}
