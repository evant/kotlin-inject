import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

val libs = the<LibrariesForLibs>()

kotlin {
    jvmToolchain(17)
}

kotlin.compilerOptions.jvmTarget = JvmTarget.JVM_1_8

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Ensure xml test reports are generated
tasks.withType<AbstractTestTask>().configureEach {
    reports {
        junitXml.required.set(true)
    }
}
