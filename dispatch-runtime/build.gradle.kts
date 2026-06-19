plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    api(libs.compose.runtime)
    api(libs.compose.runtime.saveable)
    api(libs.coroutines.core)
    api(libs.mordant)
    api(libs.mordant.coroutines)
}
