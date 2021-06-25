plugins {
    kotlin("jvm")
    `maven-publish`
    signing
    id("de.marcphilipp.nexus-publish")
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":kotlin-inject-compiler:core"))
    // for access to CompilationErrorException until ksp properly fails on errors
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    implementation("com.google.devtools.ksp:symbol-processing-api:${Versions.ksp}")
}

publishToMavenCentral("kotlin-inject-compiler-ksp")