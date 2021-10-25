enableFeaturePreview("VERSION_CATALOGS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    }
}

rootProject.name = "kotlin-inject"

include(":kotlin-inject-runtime")
include(":kotlin-inject-compiler:core")
include(":kotlin-inject-compiler:kapt")
include(":kotlin-inject-compiler:ksp")
include(":compiler-tests")
include(":integration-tests:kapt")
include(":integration-tests:ksp")
include(":integration-tests:module")
include(":integration-tests:kapt-companion")
include(":integration-tests:ksp-companion")