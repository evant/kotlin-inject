import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version Versions.kotlin apply false
    id("com.google.devtools.ksp") version Versions.ksp apply false
    id("io.gitlab.arturbosch.detekt") version "1.17.1"
}

repositories {
    mavenCentral()
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
    afterEvaluate {
        tasks.findByName("check")?.dependsOn(rootProject.tasks["detekt"])
    }
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "1.8"
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
}

tasks.withType<Detekt>().configureEach {
    parallel = true
    buildUponDefaultConfig = true
    source = project.fileTree(projectDir)
    config.from(project.file(".static/detekt-config.yml"))
    include("**/*.kt")
    exclude("**/build/**")
    autoCorrect = findProperty("fix") == "true"
    jvmTarget = "1.8"
    reports {
        html {
            enabled = true
            destination = project.file("build/reports/detekt.html")
        }
        xml.enabled = false
    }
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.14.2")
}