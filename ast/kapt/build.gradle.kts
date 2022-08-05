plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":ast:ast-core"))
    implementation(libs.kotlinx.metadata.jvm)
    compileOnly(libs.jdk.compiler)
}