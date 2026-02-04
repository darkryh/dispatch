plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    api(projects.dispatchUpdate)
    api(libs.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
}
