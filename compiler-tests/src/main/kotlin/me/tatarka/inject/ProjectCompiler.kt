package me.tatarka.inject

import com.tschuchort.compiletesting.*
import me.tatarka.inject.compiler.Options
import me.tatarka.inject.compiler.Profiler
import me.tatarka.inject.compiler.kapt.InjectCompiler
import me.tatarka.inject.compiler.ksp.InjectProcessor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class ProjectCompiler(
    private val target: Target,
    private val root: File? = null,
    private val profiler: Profiler? = null
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
        val output = ByteArrayOutputStream()
        val printOut = PrintStream(output)
        val oldErr = System.err
        System.setErr(printOut)
        val result = KotlinCompilation().apply {
            sources = sourceFiles
            messageOutputStream = printOut
            root?.let { workingDir = it }
            inheritClassPath = true
            when (target) {
                Target.kapt -> {
                    options?.let {
                        kaptArgs.putAll(it.toMap())
                    }
                    annotationProcessors = listOf(InjectCompiler(profiler))
                }
                Target.ksp -> {
                    options?.let {
                        //TODO: wait on https://github.com/tschuchortdev/kotlin-compile-testing/pull/66
//                        kspArgs.putAll(it.toMap())
                    }
                    symbolProcessors = listOf(InjectProcessor(profiler))
                }
            }
        }.compile()
        System.setErr(oldErr)

        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            throw Exception(output.toString())
        }
    }
}

enum class Target {
    kapt,
    ksp
}