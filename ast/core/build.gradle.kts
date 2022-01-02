plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    api(libs.kotlinpoet)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.assertk)
}