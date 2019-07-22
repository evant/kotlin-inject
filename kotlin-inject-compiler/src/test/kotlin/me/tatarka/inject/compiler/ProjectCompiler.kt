package me.tatarka.inject.compiler

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
        val settingsFile = root.resolve("settings.gradle")

        settingsFile.writeText(
            """
           include ':kotlin-inject-compiler'
           include ':kotlin-inject-runtime'
           project(':kotlin-inject-compiler').projectDir = new File('${File(".").absolutePath}')
           project(':kotlin-inject-runtime').projectDir = new File('${File("../kotlin-inject-runtime").absolutePath}')
        """.trimIndent()
        )

        val buildFile = root.resolve("build.gradle")

        buildFile.writeText(
            """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.3.31'
                id 'org.jetbrains.kotlin.kapt' version '1.3.31'
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.3.31'
                kapt project(':kotlin-inject-compiler')
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
