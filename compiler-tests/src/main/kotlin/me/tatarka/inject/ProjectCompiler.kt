package me.tatarka.inject

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspArgs
import com.tschuchort.compiletesting.symbolProcessorProviders
import me.tatarka.inject.compiler.Options
import me.tatarka.inject.compiler.kapt.InjectCompiler
import me.tatarka.inject.compiler.ksp.InjectProcessorProvider
import java.io.File

class ProjectCompiler(
    private val target: Target,
    private val root: File? = null,
) {

    private val sourceFiles = mutableListOf<SourceFile>()
    private var options: Options? = null

    fun source(fileName: String, source: String): ProjectCompiler {
        sourceFiles.add(SourceFile.kotlin(fileName, source))
        return this
    }

    fun options(options: Options): ProjectCompiler {
        this.options = options
        return this
    }

    fun compile() {
        val result = KotlinCompilation().apply {
            sources = sourceFiles
            root?.let { workingDir = it }
            inheritClassPath = true
            when (target) {
                Target.KAPT -> {
                    options?.let {
                        kaptArgs.putAll(it.toMap())
                    }
                    annotationProcessors = listOf(InjectCompiler())
                }
                Target.KSP -> {
                    options?.let {
                        kspArgs.putAll(it.toMap())
                    }
                    symbolProcessorProviders = listOf(InjectProcessorProvider())
                }
            }
        }.compile()

        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            @Suppress("TooGenericExceptionThrown")
            throw Exception(result.messages)
        }
    }
}

enum class Target {
    KAPT,
    KSP
}