dependencyResolutionManagement {
    versionCatalogs {
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
