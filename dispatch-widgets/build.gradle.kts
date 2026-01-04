plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    api(projects.dispatchRuntime)
    api(projects.dispatchLayout)
    api(projects.dispatchViewmodel)
    implementation(libs.mordant.markdown)
}
