import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    api(project(":ast:core"))
    api(libs.ksp)
    implementation(libs.kotlinpoet.ksp)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview"
}
