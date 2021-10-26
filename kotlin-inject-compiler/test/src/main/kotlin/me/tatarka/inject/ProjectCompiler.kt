package me.tatarka.inject

import androidx.room.compiler.processing.util.Source
import androidx.room.compiler.processing.util.compiler.TestCompilationArguments
import androidx.room.compiler.processing.util.compiler.TestCompilationResult
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import me.tatarka.inject.compiler.Options
import me.tatarka.inject.compiler.kapt.InjectCompiler
import me.tatarka.inject.compiler.ksp.InjectProcessorProvider
import java.io.File
import javax.tools.Diagnostic

class ProjectCompiler(
    private val target: Target,
    private val workingDir: File,
) {

    private val sourceFiles = mutableListOf<Source>()
    private var options: Options? = null
    private val symbolProcessors = mutableListOf<SymbolProcessorProvider>()

    fun source(fileName: String, source: String): ProjectCompiler {
        sourceFiles.add(Source.kotlin(fileName, source))
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
        var args = TestCompilationArguments(
            sources = sourceFiles,
            processorOptions = options?.toMap().orEmpty()
        )
        args = when (target) {
            Target.KAPT -> args.copy(kaptProcessors = listOf(InjectCompiler()))
            Target.KSP -> {
                val processors = mutableListOf<SymbolProcessorProvider>()
                processors.add(InjectProcessorProvider())
                processors.addAll(symbolProcessors)
                args.copy(
                    symbolProcessorProviders = processors
                )
            }
        }
        val result = androidx.room.compiler.processing.util.compiler.compile(workingDir = workingDir, arguments = args)

        if (!result.success) {
            @Suppress("TooGenericExceptionThrown")
            throw Exception(result.diagnostics
                .filter { it.key == Diagnostic.Kind.ERROR }
                .flatMap { it.value }.joinToString {
                    StringBuilder().apply {
                        append(it.msg)
                        it.location?.source?.let {
                            append(" ")
                            append(it.relativePath)
                        }
                    }.toString()
                })
        }

        return result
    }
}

enum class Target {
    KAPT,
    KSP
}