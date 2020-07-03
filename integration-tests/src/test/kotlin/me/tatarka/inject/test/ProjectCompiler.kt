package me.tatarka.inject.test

import assertk.Assert
import assertk.assertions.isInstanceOf
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import java.io.File

class ProjectCompiler(private val root: File) {

    private val sources = mutableListOf<Pair<String, String>>()

    fun source(fileName: String, source: String): ProjectCompiler {
        sources.add(fileName to source)
        return this
    }

    fun compile() {
        val kotlinVersion = "1.4-M1"

        val settingsFile = root.resolve("settings.gradle")

        settingsFile.writeText(
           """
           pluginManagement {
               repositories {
                   google()
                   maven { url "https://dl.bintray.com/kotlin/kotlin-eap" }
                   mavenCentral()
                   gradlePluginPortal()
               }
               resolutionStrategy {
                   eachPlugin {
                       switch (requested.id.id) {
                           case "kotlin-ksp":
                           case "org.jetbrains.kotlin.kotlin-ksp":
                           case "org.jetbrains.kotlin.ksp":
                               useModule("org.jetbrains.kotlin:kotlin-ksp:" + requested.version)
                       }
                   }
               }
           }
           
           include ':kotlin-inject-compiler'
           include ':kotlin-inject-runtime'
           project(':kotlin-inject-compiler').projectDir = new File('${File("../kotlin-inject-compiler").absolutePath}')
           project(':kotlin-inject-runtime').projectDir = new File('${File("../kotlin-inject-runtime").absolutePath}')
        """.trimIndent()
        )

        val buildFile = root.resolve("build.gradle")

        buildFile.writeText(
            """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '${kotlinVersion}'
            //    id 'org.jetbrains.kotlin.kapt' version '${kotlinVersion}'
                id("org.jetbrains.kotlin.ksp") version "1.4-M1-dev-experimental-20200610"
            }
            
            allprojects {
                logging.captureStandardError LogLevel.QUIET
            
                repositories {
                    google()
                    maven { url "https://dl.bintray.com/kotlin/kotlin-eap" }
                    mavenCentral()
                }
                
                tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).all {
                    kotlinOptions {
                        jvmTarget = "1.8"
                    }
                }
            }
            
            dependencies {
                implementation 'org.jetbrains.kotlin:kotlin-stdlib'
             //   kapt project(':kotlin-inject-compiler')
                ksp project(':kotlin-inject-compiler')
                implementation project(':kotlin-inject-runtime')
            }
        """.trimIndent()
        )

        val sourceDir = root.resolve("src/main/kotlin")
        sourceDir.mkdirs()

        for ((file, source) in sources) {
            val sourceFile = sourceDir.resolve(file)
            sourceFile.writeText(source)
        }

        GradleRunner.create()
            .withProjectDir(root)
            .withArguments("assemble")
            .build()
    }
}

fun Assert<Throwable>.output() = isInstanceOf(UnexpectedBuildFailure::class).transform { actual ->
    actual.buildResult.output
}
