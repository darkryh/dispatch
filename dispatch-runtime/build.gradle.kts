plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    api(libs.coroutines.core)
    api(libs.mordant)
    api(libs.mordant.coroutines)
}
