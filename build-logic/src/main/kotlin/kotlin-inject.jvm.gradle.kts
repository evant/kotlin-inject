import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

val libs = the<LibrariesForLibs>()

kotlin.compilerOptions.jvmTarget = libs.versions.jvmTarget.map(JvmTarget::fromTarget)

java {
    toolchain {
        languageVersion.set(libs.versions.jdk.map(JavaLanguageVersion::of))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(libs.versions.jvmTarget.map(String::toInt))
}

// Ensure xml test reports are generated
tasks.withType<AbstractTestTask>().configureEach {
    reports {
        junitXml.required.set(true)
    }
}
