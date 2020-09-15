package me.tatarka.inject.test

import assertk.Assert
import assertk.assertions.isNotNull
import assertk.assertions.message
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessors
import me.tatarka.inject.compiler.kapt.InjectCompiler
import me.tatarka.inject.compiler.kapt.ScopedInjectCompiler
import me.tatarka.inject.compiler.ksp.InjectProcessor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class ProjectCompiler(private val root: File, private val target: Target) {

    private val sourceFiles = mutableListOf<SourceFile>()

    fun source(fileName: String, source: String): ProjectCompiler {
        sourceFiles.add(SourceFile.kotlin(fileName, source))
        return this
    }

    fun compile() {
        val output = ByteArrayOutputStream()
        val printOut = PrintStream(output)
        val oldErr = System.err
        System.setErr(printOut)
        val result = KotlinCompilation().apply {
            sources = sourceFiles
            messageOutputStream = printOut
            workingDir = root
            inheritClassPath = true
            when (target) {
                Target.kapt -> {
                    annotationProcessors = listOf(InjectCompiler(), ScopedInjectCompiler())
                }
                Target.ksp -> {
                    symbolProcessors = listOf(InjectProcessor())
                }
            }
        }.compile()
        System.setErr(oldErr)

        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            throw Exception(output.toString())
        }
    }
}

fun Assert<Throwable>.output(): Assert<String> = message().isNotNull()

enum class Target {
    kapt,
    ksp
}