import java.util.Locale

plugins {
    `maven-publish`
    signing
}

group = rootProject.group
version = rootProject.version

fun MavenPublication.mavenCentralPom() {
    pom {
        name.set("kotlin-inject")
        description.set("A compile-time dependency injection library for kotlin")
        url.set("https://github.com/evant/kotlin-inject")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("evant")
                name.set("Evan Tatarka")
            }
        }
        scm {
            connection.set("https://github.com/evant/kotlin-inject.git")
            developerConnection.set("https://github.com/evant/kotlin-inject.git")
            url.set("https://github.com/evant/kotlin-inject")
        }
    }
}

publishing {
    if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
        // already has publications, just need to add javadoc task
        val javadocJar by tasks.creating(Jar::class) {
            from("javadoc")
            archiveClassifier.set("javadoc")
        }
        publications.all {
            if (this is MavenPublication) {
                artifact(javadocJar)
                mavenCentralPom()
            }
        }
        // create task to publish all apple (macos, ios, tvos, watchos) artifacts
        @Suppress("UNUSED_VARIABLE")
        val publishApple by tasks.registering {
            publications.all {
                if (name.contains(Regex("macos|ios|tvos|watchos"))) {
                    dependsOn("publish${name.capitalize(Locale.ROOT)}ToSonatypeRepository")
                }
            }
        }
    } else {
        // Need to create source, javadoc & publication
        val java = extensions.getByType<JavaPluginExtension>()
        java.withSourcesJar()
        java.withJavadocJar()
        publications {
            create<MavenPublication>("lib") {
                from(components["java"])
                mavenCentralPom()
                // We want the artifactId to represent the full project path
                artifactId = path
                    .trimStart(':')
                    .replace(":", "-")
            }
        }
    }
}

signing {
    setRequired {
        findProperty("signing.keyId") != null
    }

    publishing.publications.all {
        sign(this)
    }
}