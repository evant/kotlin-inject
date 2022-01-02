package me.tatarka.inject.compiler.kapt

import me.tatarka.inject.compiler.Options
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.SourceVersion

private const val OPTION_GENERATE_COMPANION_EXTENSIONS = "me.tatarka.inject.generateCompanionExtensions"

abstract class BaseInjectCompiler : AbstractProcessor() {

    protected lateinit var options: Options
    protected lateinit var filer: Filer
    protected lateinit var env: ProcessingEnvironment

    open override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        this.options = Options.from(processingEnv.options)
        this.env = processingEnv
        this.filer = processingEnv.filer
    }

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun getSupportedOptions(): Set<String> = setOf(OPTION_GENERATE_COMPANION_EXTENSIONS)
}