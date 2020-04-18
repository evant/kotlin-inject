package me.tatarka.inject.compiler

import me.tatarka.inject.compiler.ast.AstProvider
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

private const val OPTION_GENERATE_COMPANION_EXTENSIONS = "me.tatarka.inject.generateCompanionExtensions"

abstract class BaseInjectCompiler : AbstractProcessor(), AstProvider {

    var generateCompanionExtensions: Boolean = false
        private set

    protected lateinit var filer: Filer
    override lateinit var types: Types
    override lateinit var elements: Elements
    override lateinit var messager: Messager

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        this.generateCompanionExtensions =
            processingEnv.options[OPTION_GENERATE_COMPANION_EXTENSIONS]?.toBoolean() ?: false
        this.filer = processingEnv.filer
        this.types = processingEnv.typeUtils
        this.elements = processingEnv.elementUtils
        this.messager = processingEnv.messager
    }

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun getSupportedOptions(): Set<String> = setOf(OPTION_GENERATE_COMPANION_EXTENSIONS)
}