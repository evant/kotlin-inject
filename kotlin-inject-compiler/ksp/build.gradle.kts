plugins {
    kotlin("jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":kotlin-inject-compiler:core"))
    // for access to CompilationErrorException until ksp properly fails on errors
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.ksp)
}