import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    kotlin("jvm")
}

val libs = the<LibrariesForLibs>()

dependencies {
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
