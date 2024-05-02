import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlin-inject-runtime"))
            }
        }
    }
}
