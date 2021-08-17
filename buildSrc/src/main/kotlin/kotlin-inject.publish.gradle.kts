import de.marcphilipp.gradle.nexus.NexusPublishExtension
import java.time.Duration

plugins {
    `maven-publish`
    signing
    id("de.marcphilipp.nexus-publish")
}

group = "me.tatarka.inject"
version = Versions.kotlinInject

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

extensions.configure<NexusPublishExtension> {
    repositories {
        sonatype()
    }
    @Suppress("MagicNumber")
    val publishTimeout = Duration.ofMinutes(5L)
    clientTimeout.set(publishTimeout)
    connectTimeout.set(publishTimeout)
}

publishing {
    if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
        // already has publications, just need to add javadoc task
        val javadocJar = tasks.create<Jar>("javadocJar") {
            from("javadoc")
            archiveClassifier.set("javadoc")
        }
        publishing.publications.all {
            (this as MavenPublication).artifact(javadocJar)
            mavenCentralPom()
        }
    } else {
        // Need to create source, javadoc & publication
        val java = extensions.getByType<JavaPluginExtension>()
        java.withSourcesJar()
        java.withJavadocJar()
        publishing.publications {
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