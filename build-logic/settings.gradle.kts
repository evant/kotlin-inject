dependencyResolutionManagement {
    versionCatalogs {
        @Suppress("UNUSED_VARIABLE")
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
