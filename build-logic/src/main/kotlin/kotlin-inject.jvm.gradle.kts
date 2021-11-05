import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    kotlin("jvm")
}

val libs = the<LibrariesForLibs>()

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Ensure xml test reports are generated
tasks.withType<AbstractTestTask>().configureEach {
    reports {
        junitXml.required.set(true)
    }
}
