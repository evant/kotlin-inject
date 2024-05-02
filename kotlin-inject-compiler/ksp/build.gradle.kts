plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime-kmp"))
    implementation(project(":kotlin-inject-compiler:kotlin-inject-compiler-core"))
    implementation(project(":ast:ast-ksp"))
    implementation(libs.ksp)
    implementation(libs.kotlinpoet.ksp)
}
