import de.marcphilipp.gradle.nexus.NexusPublishExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningExtension
import java.time.Duration

fun MavenPublication.mavenCentralPom(artifactId: String? = null) {
    if (artifactId != null) {
        this.artifactId = artifactId
    }
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

private const val PUBLISH_TIMEOUT = 5L

fun Project.publishToMavenCentral(artifactId: String? = null) {
    group = "me.tatarka.inject"
    version = Versions.kotlinInject

    extensions.configure<NexusPublishExtension> {
        repositories {
            sonatype()
        }
        clientTimeout.set(Duration.ofMinutes(PUBLISH_TIMEOUT))
        connectTimeout.set(Duration.ofMinutes(PUBLISH_TIMEOUT))
    }
    extensions.configure<SigningExtension> {
        setRequired {
            findProperty("signing.keyId") != null
        }
        val publishing = extensions.getByType<PublishingExtension>()

        if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
            // already has publications, just need to add javadoc task
            val javadocJar = tasks.create<Jar>("javadocJar") {
                from("javadoc")
                archiveClassifier.set("javadoc")
            }
            publishing.publications.all {
                (this as MavenPublication).artifact(javadocJar)
                mavenCentralPom(artifactId)
            }
        } else {
            // Need to create source, javadoc & publication
            val java = extensions.getByType<JavaPluginExtension>()
            java.withSourcesJar()
            java.withJavadocJar()
            publishing.publications {
                create<MavenPublication>("lib") {
                    from(components["java"])
                    mavenCentralPom(artifactId)
                }
            }
        }

        publishing.publications.all {
            sign(this)
        }
    }
}