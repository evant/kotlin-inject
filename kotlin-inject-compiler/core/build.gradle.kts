plugins {
    kotlin("jvm")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    api("com.squareup:kotlinpoet:1.5.0")
}