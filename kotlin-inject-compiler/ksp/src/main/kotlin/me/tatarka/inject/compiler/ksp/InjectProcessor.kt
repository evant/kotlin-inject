package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.compiler.COMPONENT
import me.tatarka.inject.compiler.FailedToGenerateException
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.Options
import me.tatarka.inject.compiler.PROVIDES
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.KSAstProvider

class InjectProcessor(
    private val options: Options,
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private lateinit var provider: KSAstProvider
    private lateinit var generator: InjectGenerator
    private var deferred: MutableList<KSClassDeclaration> = mutableListOf()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        provider = KSAstProvider(resolver, logger)
        generator = InjectGenerator(provider, options)

        val previousDiffered = deferred

        deferred = mutableListOf()

        for (
        element in previousDiffered + resolver.getSymbolsWithClassAnnotation(
            COMPONENT.packageName,
            COMPONENT.simpleName,
        )
        ) {
            with(provider) {
                val astClass = element.toAstClass()
                if (validate(element)) {
                    process(astClass)
                } else {
                    deferred.add(element)
                }
            }
        }

        return deferred
    }

    private fun process(astClass: AstClass) {
        try {
            val file = generator.generate(astClass)
            file.writeTo(codeGenerator, aggregating = true)
        } catch (e: FailedToGenerateException) {
            provider.error(e.message.orEmpty(), e.element)
            // Continue so we can see all errors
        }
    }

    override fun finish() {
        // Last round, generate as much as we can, reporting errors for types that still can't be resolved.
        for (element in deferred) {
            with(provider) {
                val astClass = element.toAstClass()
                process(astClass)
            }
        }
        deferred = mutableListOf()
    }

    private fun validate(declaration: KSClassDeclaration): Boolean {
        return declaration.accept(
            FixedKSValidateVisitor { node, _ ->
                when (node) {
                    is KSFunctionDeclaration ->
                        node.getVisibility() != Visibility.PRIVATE &&
                                (node.isAbstract || node.hasAnnotation(PROVIDES.packageName, PROVIDES.simpleName))
                    is KSPropertyDeclaration ->
                        node.getVisibility() != Visibility.PRIVATE &&
                                (
                                        node.isAbstract() ||
                                                node.getter?.hasAnnotation(
                                                    PROVIDES.packageName,
                                                    PROVIDES.simpleName
                                                ) ?: true
                                        )
                    else -> true
                }
            },
            null
        )
    }
}

class InjectProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return InjectProcessor(
            Options.from(environment.options),
            environment.codeGenerator,
            environment.logger
        )
    }
}