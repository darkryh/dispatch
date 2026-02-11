plugins {
    id("dispatch.kotlin-library-conventions")
}

dependencies {
    api(projects.koogAgentBenchmarkCore)

    implementation(libs.koog.agents)
    implementation(libs.koog.features.event.handler)
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.coroutines.test)
}
