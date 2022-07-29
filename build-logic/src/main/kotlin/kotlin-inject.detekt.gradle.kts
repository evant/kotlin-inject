import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val libs = the<LibrariesForLibs>()

dependencies {
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Detekt>().configureEach {
    parallel = true
    buildUponDefaultConfig = true
    config.from(file(rootProject.projectDir.resolve(".static/detekt-config.yml")))
    reports {
        html.required.set(true)
        xml.required.set(false)
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val checkApple by rootProject.tasks.getting {
    val detekt by tasks.getting
    dependsOn(detekt)
}