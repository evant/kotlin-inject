plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    api(project(":ast:ast-core"))
    api(libs.ksp)
    implementation(libs.kotlinpoet.ksp)
}
