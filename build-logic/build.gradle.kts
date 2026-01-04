plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.dokka)
    implementation(libs.publish)

    // For accessing version catalog in convention plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
