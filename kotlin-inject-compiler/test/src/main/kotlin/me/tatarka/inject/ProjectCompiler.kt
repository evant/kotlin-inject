@file:OptIn(ExperimentalCompilerApi::class)

package me.tatarka.inject

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.DiagnosticSeverity
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import me.tatarka.inject.compiler.Options
import me.tatarka.inject.compiler.ksp.InjectProcessorProvider
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

class ProjectCompiler(
    private val target: Target,
    private val workingDir: File,
) {

    private val sourceFiles = mutableListOf<SourceFile>()
    private var options: Options? = null
    private val symbolProcessors = mutableListOf<SymbolProcessorProvider>()

    fun source(fileName: String, @Language("kotlin") source: String): ProjectCompiler {
        sourceFiles.add(SourceFile.kotlin(fileName, source))
        return this
    }

    fun options(options: Options): ProjectCompiler {
        this.options = options
        return this
    }

    fun symbolProcessor(processor: SymbolProcessorProvider): ProjectCompiler {
        symbolProcessors.add(processor)
        return this
    }

    fun compile(): TestCompilationResult {
        val result = TestCompilationResult(
            KotlinCompilation().apply {
                workingDir = this@ProjectCompiler.workingDir
                sources = sourceFiles
                when (target) {
                    Target.KSP -> {
                        options?.toMap()?.let { kspProcessorOptions.putAll(it) }
                        symbolProcessorProviders = mutableListOf<SymbolProcessorProvider>().apply {
                            add(InjectProcessorProvider())
                            addAll(symbolProcessors)
                        }
                    }
                }
                inheritClassPath = true
                // work-around for https://github.com/ZacSweers/kotlin-compile-testing/issues/197
                kspWithCompilation = true
                messageOutputStream = System.out
            }.compile()
        )

        if (!result.success) {
            @Suppress("TooGenericExceptionThrown")
            throw Exception(result.output(DiagnosticSeverity.ERROR))
        }
        return result
    }
}

enum class Target {
    KSP
}

class TestCompilationResult(private val result: CompilationResult) {
    val success: Boolean
        get() = result.exitCode == KotlinCompilation.ExitCode.OK

    @OptIn(ExperimentalCompilerApi::class)
    fun output(vararg severities: DiagnosticSeverity): String = when {
        severities.isEmpty() -> result.messages
        else -> result.messagesWithSeverity(*severities)
    }
}
