plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    api(libs.koog.agents)
    api(libs.koog.features.memory)
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.coroutines.test)
}
