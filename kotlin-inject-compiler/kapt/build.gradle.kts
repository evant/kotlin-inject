plugins {
    kotlin("jvm")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":kotlin-inject-compiler:core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.2.0")
    compileOnly("io.earcam.wrapped:jdk.compiler:1.8.132")
}