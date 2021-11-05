import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val libs = the<LibrariesForLibs>()

detekt {
    parallel = true
    buildUponDefaultConfig = true
    config.from(file(rootProject.projectDir.resolve(".static/detekt-config.yml")))
    autoCorrect = findProperty("fix") == "true"
    reports {
        html.enabled = true
        xml.enabled = false
    }
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Test>().configureEach {
    ignoreFailures = true
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val checkApple by rootProject.tasks.getting {
    val detekt by tasks.getting
    dependsOn(detekt)
}