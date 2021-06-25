plugins {
    kotlin("jvm")
    `maven-publish`
    signing
    id("de.marcphilipp.nexus-publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    api("com.squareup:kotlinpoet:1.5.0")
}

publishToMavenCentral("kotlin-inject-compiler-core")