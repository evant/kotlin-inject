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
import com.google.devtools.ksp.symbol.KSName
import me.tatarka.inject.compiler.COMPONENT
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.KMP_COMPONENT_CREATE
import me.tatarka.inject.compiler.KmpComponentCreateGenerator
import me.tatarka.inject.compiler.Options
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstFunction
import me.tatarka.kotlin.ast.KSAstProvider

class InjectProcessor(
    private val options: Options,
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private lateinit var provider: KSAstProvider
    private lateinit var injectGenerator: InjectGenerator
    private lateinit var kmpComponentCreateGenerator: KmpComponentCreateGenerator
    private var lastResolver: Resolver? = null
    private var deferredClassNames: List<KSName> = mutableListOf()
    private var deferredFunctionNames: List<KSName> = mutableListOf()

    private val kmpComponentCreateFunctionsByComponentType = mutableMapOf<AstClass, MutableList<AstFunction>>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        lastResolver = resolver
        provider = KSAstProvider(resolver, logger)
        injectGenerator = InjectGenerator(provider, options)
        kmpComponentCreateGenerator = KmpComponentCreateGenerator(provider, options)

        val componentSymbols =
            resolver.getSymbolsWithAnnotation(COMPONENT.canonicalName).filterIsInstance<KSClassDeclaration>()
        val deferredClasses = componentSymbols.filterNot { element ->
            processInject(element, provider, codeGenerator, injectGenerator)
        }.toList()
        deferredClassNames = deferredClasses.mapNotNull { it.qualifiedName }

        val kmpComponentCreateSymbols = resolver.getSymbolsWithAnnotation(KMP_COMPONENT_CREATE.canonicalName)
            .filterIsInstance<KSFunctionDeclaration>()
        val deferredFunctions = kmpComponentCreateSymbols.filterNot { element ->
            processKmpComponentCreate(element, provider, kmpComponentCreateFunctionsByComponentType)
        }.toList()
        deferredFunctionNames = deferredFunctions.mapNotNull { it.qualifiedName }

        return deferredClasses + deferredFunctions
    }

    override fun finish() {
        try {
            // Last round, generate as much as we can, reporting errors for types that still can't be resolved.
            val resolver = lastResolver ?: return
            for (name in deferredClassNames) {
                val element = resolver.getClassDeclarationByName(name)
                if (element == null) {
                    logger.error("Failed to resolve: $name")
                    continue
                }
                processInject(
                    element,
                    provider,
                    codeGenerator,
                    injectGenerator,
                    skipValidation = true
                )
            }

            for (name in deferredFunctionNames) {
                val element = resolver.getFunctionDeclarationsByName(
                    name,
                    includeTopLevel = true
                ).firstOrNull()
                if (element == null) {
                    logger.error("Failed to resolve: $name")
                    continue
                }
                processKmpComponentCreate(element, provider, kmpComponentCreateFunctionsByComponentType)
            }

            generateKmpComponentCreateFiles(
                codeGenerator,
                kmpComponentCreateGenerator,
                kmpComponentCreateFunctionsByComponentType
            )
            kmpComponentCreateFunctionsByComponentType.clear()
        } finally {
            lastResolver = null
            deferredClassNames = emptyList()
            deferredFunctionNames = mutableListOf()
        }
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
