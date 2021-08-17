pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
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