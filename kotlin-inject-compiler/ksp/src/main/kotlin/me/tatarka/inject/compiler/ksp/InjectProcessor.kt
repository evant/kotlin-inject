package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import me.tatarka.inject.compiler.COMPONENT
import me.tatarka.inject.compiler.FailedToGenerateException
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.Options

class InjectProcessor(
    private val options: Options,
    private val codeGenerator: CodeGenerator,
    override val logger: KSPLogger,
) : SymbolProcessor, KSAstProvider {

    override lateinit var resolver: Resolver

    @Suppress("LoopWithTooManyJumpStatements")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        this.resolver = resolver

        val generator = InjectGenerator(this, options)

        for (element in resolver.getSymbolsWithClassAnnotation(COMPONENT.packageName, COMPONENT.simpleName)) {
            val astClass = element.toAstClass()

            try {
                val file = generator.generate(astClass)
                file.writeTo(codeGenerator)
            } catch (e: FailedToGenerateException) {
                error(e.message.orEmpty(), e.element)
                // Continue so we can see all errors
                continue
            }
        }

        return emptyList()
    }
}

class InjectProcessorProvider : SymbolProcessorProvider {
    override fun create(
        options: Map<String, String>,
        kotlinVersion: KotlinVersion,
        codeGenerator: CodeGenerator,
        logger: KSPLogger
    ): SymbolProcessor {
        return InjectProcessor(Options.from(options), codeGenerator, logger)
    }
}