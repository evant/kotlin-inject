@file:OptIn(ExperimentalCompilerApi::class)

package me.tatarka.inject

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.DiagnosticSeverity
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KspTool
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspWithCompilation
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

                if (target == Target.KSP1) {
                    languageVersion = "1.9"
                }

                val ksp: KspTool.() -> Unit = {
                    options?.toMap()?.let { kspProcessorOptions.putAll(it) }
                    symbolProcessorProviders.add(InjectProcessorProvider())
                    symbolProcessorProviders.addAll(symbolProcessors)
                }

                when (target) {
                    Target.KSP1 -> configureKsp(useKsp2 = false, ksp)
                    Target.KSP2 -> configureKsp(useKsp2 = true, ksp)
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

private fun String.filterByKind(vararg kind: DiagnosticSeverity): String = buildString {
    var currentKind: DiagnosticSeverity? = null
    for (line in this@filterByKind.lineSequence()) {
        val lineKind = line.matchLine()
        if (lineKind != null) {
            currentKind = lineKind
        }
        if (currentKind in kind) {
            append(line)
            append('\n')
        }
    }
}

private fun String.matchLine(): DiagnosticSeverity? {
    if (length < 2) return null
    val matchedKind = when (get(0)) {
        'e' -> DiagnosticSeverity.ERROR
        'w' -> DiagnosticSeverity.WARNING
        'v' -> DiagnosticSeverity.LOGGING
        else -> null
    } ?: return null

    return if (get(1) == ':') {
        matchedKind
    } else {
        null
    }
}

enum class Target {
    KSP1,
    KSP2
}

class TestCompilationResult(private val result: CompilationResult) {
    val success: Boolean
        get() = result.exitCode == KotlinCompilation.ExitCode.OK

    fun output(vararg severities: DiagnosticSeverity): String =
        result.messages.filterByKind(*severities)
}
