import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

val libs = the<LibrariesForLibs>()

kotlin {
    jvmToolchain(17)
}

kotlin.compilerOptions.jvmTarget = JvmTarget.JVM_11

java {
    targetCompatibility = JavaVersion.VERSION_11
}

// Ensure xml test reports are generated
tasks.withType<AbstractTestTask>().configureEach {
    reports {
        junitXml.required.set(true)
    }
}
