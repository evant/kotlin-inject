plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime-kmp"))
    api(project(":ast:ast-core"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.assertk)
}
