plugins {
    kotlin("jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    api(libs.kotlinpoet.core)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}