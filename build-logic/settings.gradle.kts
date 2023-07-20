dependencyResolutionManagement {
    versionCatalogs {
        @Suppress("UNUSED_VARIABLE")
        val libs by registering {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kotlin-inject-conventions"
