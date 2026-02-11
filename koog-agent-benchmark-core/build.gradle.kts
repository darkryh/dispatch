plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.coroutines.test)
}
