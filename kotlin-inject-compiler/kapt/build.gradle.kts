plugins {
    kotlin("jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":kotlin-inject-compiler:core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation(libs.kotlinx.metadata.jvm)
    compileOnly(libs.jdk.compiler)
}