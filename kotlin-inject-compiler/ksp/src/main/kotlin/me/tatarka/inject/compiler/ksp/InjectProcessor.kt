package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import me.tatarka.inject.compiler.COMPONENT
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.KMP_COMPONENT_CREATOR
import me.tatarka.inject.compiler.KmpComponentCreatorGenerator
import me.tatarka.inject.compiler.Options
import me.tatarka.kotlin.ast.KSAstProvider

class InjectProcessor(
    private val options: Options,
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private lateinit var provider: KSAstProvider
    private lateinit var injectGenerator: InjectGenerator
    private lateinit var kmpComponentCreatorGenerator: KmpComponentCreatorGenerator
    private var deferredClasses: List<KSClassDeclaration> = mutableListOf()
    private var deferredFunctions: List<KSFunctionDeclaration> = mutableListOf()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        provider = KSAstProvider(resolver, logger)
        injectGenerator = InjectGenerator(provider, options)
        kmpComponentCreatorGenerator = KmpComponentCreatorGenerator(provider, options)

        val previousDeferredClasses = deferredClasses
        val previousDeferredFunctions = deferredFunctions

        val componentSymbols = previousDeferredClasses + resolver.getSymbolsWithClassAnnotation(
            packageName = COMPONENT.packageName,
            simpleName = COMPONENT.simpleName
        )
        deferredClasses = componentSymbols.filterNot { element ->
            processInject(element, provider, codeGenerator, injectGenerator)
        }

        val kmpComponentCreatorSymbols = previousDeferredFunctions + resolver.getSymbolsWithFunctionAnnotation(
            packageName = KMP_COMPONENT_CREATOR.packageName,
            simpleName = KMP_COMPONENT_CREATOR.simpleName
        )
        deferredFunctions = kmpComponentCreatorSymbols.filterNot { element ->
            processKmpComponentCreator(element, provider, codeGenerator, kmpComponentCreatorGenerator)
        }

        return deferredClasses + deferredFunctions
    }

    override fun finish() {
        // Last round, generate as much as we can, reporting errors for types that still can't be resolved.
        for (element in deferredClasses) {
            processInject(
                element,
                provider,
                codeGenerator,
                injectGenerator,
                skipValidation = true
            )
        }
        deferredClasses = mutableListOf()

        for (element in deferredFunctions) {
            processKmpComponentCreator(element, provider, codeGenerator, kmpComponentCreatorGenerator)
        }
        deferredFunctions = mutableListOf()
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
