import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":kotlin-inject-compiler:kotlin-inject-compiler-core"))
    implementation(project(":ast:ast-ksp"))
    implementation(libs.ksp)
    implementation(libs.kotlinpoet.ksp)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview"
}