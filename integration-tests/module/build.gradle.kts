plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kotlin-inject-runtime"))
            }
        }
    }
}