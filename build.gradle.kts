import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.google.devtools.ksp") version Versions.ksp apply false
    id("io.gitlab.arturbosch.detekt") version "1.18.0"
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
    }
}

val testReport = tasks.register<TestReport>("testReport") {
    destinationDir = file("$buildDir/reports")
    reportOn(subprojects.mapNotNull { it.tasks.findByPath("test") })
}

val copyTestResults = tasks.register<Copy>("copyTestResults") {
    from(files(subprojects.map { file("${it.buildDir}/test-results") })) {
        include("**/*.xml")
    }
    destinationDir = file("$buildDir/test-results")
    includeEmptyDirs = false
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
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.17.1")
}
repositories {
    mavenCentral()
}

subprojects {
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

    tasks.withType<Test>().configureEach {
        finalizedBy(testReport, copyTestResults)
        ignoreFailures = true
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
