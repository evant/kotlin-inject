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
    api(libs.kotlinpoet)
}