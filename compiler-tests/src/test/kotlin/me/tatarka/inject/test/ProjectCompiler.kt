package me.tatarka.inject.test

import assertk.Assert
import assertk.assertions.isInstanceOf
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import java.io.File

class ProjectCompiler(private val root: File, private val target: Target) {

    private lateinit var sourceDir: File

    fun setup(): ProjectCompiler {
        val kotlinVersion = "1.4.0"

        val settingsFile = root.resolve("settings.gradle")

        val pluginManagement = """
           pluginManagement {
               repositories {
                   google()
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
        """.trimIndent()

        settingsFile.writeText(
            """
            ${if (target == Target.ksp) pluginManagement else ""}
           
           include ':kotlin-inject-compiler-core'
           include ':kotlin-inject-compiler-${target}'
           include ':kotlin-inject-runtime'
           include ':compiler-test'
           project(':kotlin-inject-compiler-core').projectDir = new File('${File("../kotlin-inject-compiler-core").absolutePath}')
           project(':kotlin-inject-compiler-${target}').projectDir = new File('${File("../kotlin-inject-compiler-${target}").absolutePath}')
           project(':kotlin-inject-runtime').projectDir = new File('${File("../kotlin-inject-runtime").absolutePath}')
        """.trimIndent()
        )

        val rootBuildFile = root.resolve("build.gradle")

        rootBuildFile.writeText(
            """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '${kotlinVersion}' apply false
            }
                
            allprojects {
                repositories {
                    google()
                    mavenCentral()
                }
            }
            """.trimIndent()
        )

        val dir = root.resolve("compiler-test")
        dir.mkdirs()

        val buildFile = dir.resolve("build.gradle")

        buildFile.writeText(
            """
            plugins {
                id 'org.jetbrains.kotlin.jvm'
                id 'org.jetbrains.kotlin.$target' ${if (target == Target.ksp) " version '1.4.0-dev-experimental-20200914'" else ""}
            }
            
            dependencies {
                implementation 'org.jetbrains.kotlin:kotlin-stdlib'
                $target project(':kotlin-inject-compiler-${target}')
                implementation project(':kotlin-inject-runtime')
            }
            """.trimIndent()
        )

        println(buildFile.readText())

        sourceDir = dir.resolve("src/main/kotlin")
        sourceDir.mkdirs()

        return this
    }

    fun source(fileName: String, source: String): ProjectCompiler {
        val sourceFile = sourceDir.resolve(fileName)
        sourceFile.writeText(source)
        return this
    }

    fun clear() {
        sourceDir.recursiveDelete()
        sourceDir.mkdirs()
    }

    fun compile() {
        GradleRunner.create()
            .withProjectDir(root)
            .withArguments("assemble", "--stacktrace")
            .build()
    }
}

fun Assert<Throwable>.output() = isInstanceOf(UnexpectedBuildFailure::class).transform { actual ->
    actual.buildResult.output
}

enum class Target {
    kapt, ksp
}
