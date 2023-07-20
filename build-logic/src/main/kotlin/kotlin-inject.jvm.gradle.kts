import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

val libs = the<LibrariesForLibs>()

kotlin.compilerOptions.jvmTarget = JvmTarget.JVM_11

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// Ensure xml test reports are generated
tasks.withType<AbstractTestTask>().configureEach {
    reports {
        junitXml.required.set(true)
    }
}
