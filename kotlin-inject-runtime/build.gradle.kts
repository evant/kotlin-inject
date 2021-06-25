plugins {
    kotlin("multiplatform")
    `maven-publish`
    signing
    id("de.marcphilipp.nexus-publish")
}

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
}

publishToMavenCentral()