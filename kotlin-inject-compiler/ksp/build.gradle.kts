plugins {
    kotlin("jvm")
    id("kotlin-inject.publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":kotlin-inject-compiler:core"))
    // for access to CompilationErrorException until ksp properly fails on errors
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    implementation("com.google.devtools.ksp:symbol-processing-api:${Versions.ksp}")
}