plugins {
    kotlin("jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    api(libs.kotlinpoet)
}