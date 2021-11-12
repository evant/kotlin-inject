enableFeaturePreview("VERSION_CATALOGS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "kotlin-inject"

include(":kotlin-inject-runtime")
include(":kotlin-inject-compiler:core")
include(":kotlin-inject-compiler:kapt")
include(":kotlin-inject-compiler:ksp")
include(":kotlin-inject-compiler:test")
include(":integration-tests:kapt")
include(":integration-tests:ksp")
include(":integration-tests:module")
include(":integration-tests:kapt-companion")
include(":integration-tests:ksp-companion")
include(":integration-tests:jmh")
