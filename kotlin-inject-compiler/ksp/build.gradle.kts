import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":kotlin-inject-compiler:core"))
    implementation(libs.ksp)
    implementation(libs.kotlinpoet.ksp)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview"
}