buildscript {
    dependencies {
        // solves KGP classloader issues
        classpath(libs.kotlin.gradle)
    }
}

plugins {
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.nexus.publish)
    base
}

group = "me.tatarka.inject"
version = libs.versions.kotlin.inject.get()

nexusPublishing {
    repositories {
        sonatype()
    }
}

val testReport by tasks.registering(TestReport::class) {
    destinationDirectory = layout.buildDirectory.map { it.asFile.resolve("reports") }
}

val copyTestResults by tasks.registering(Copy::class) {
    destinationDir = layout.buildDirectory.get().asFile.resolve("test-results")
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

val testReportApple by tasks.registering(TestReport::class) {
    destinationDirectory = layout.buildDirectory.map { it.asFile.resolve("reports") }
}

val copyTestResultsApple by tasks.registering(Copy::class) {
    destinationDir = layout.buildDirectory.get().asFile.resolve("test-results")
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

val check by tasks.getting
val checkApple by tasks.registering {
    finalizedBy(testReportApple, copyTestResultsApple)
}

check.finalizedBy(testReport, copyTestResults)
