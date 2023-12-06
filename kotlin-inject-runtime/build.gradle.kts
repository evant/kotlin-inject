import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("kotlin-inject.publish")
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi ::class)
    applyDefaultHierarchyTemplate {
        group("commonJs") {
            withJs()
            withWasm()
        }
    }

    sourceSets {
        nativeMain {
            dependencies {
                implementation(libs.kotlinx.atomicfu)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines)
                implementation(libs.assertk)
            }
        }
    }
}
