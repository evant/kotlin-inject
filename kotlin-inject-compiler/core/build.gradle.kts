plugins {
    kotlin("jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    api(libs.kotlinpoet)
    testImplementation(libs.bundles.kotlin.test.junit)
    testImplementation(libs.assertk)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}