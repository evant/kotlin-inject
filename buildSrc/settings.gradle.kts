enableFeaturePreview("VERSION_CATALOGS")

dependencyResolutionManagement {
    versionCatalogs {
        val libs by creating {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kotlin-inject-conventions"
