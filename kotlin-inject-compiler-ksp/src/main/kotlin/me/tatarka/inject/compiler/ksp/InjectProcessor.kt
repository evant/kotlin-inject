package me.tatarka.inject.compiler.ksp

import com.squareup.kotlinpoet.FileSpec
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.compiler.*
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.KSPLogger
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration

class InjectProcessor(private val profiler: Profiler? = null) : SymbolProcessor, KSAstProvider {

    private lateinit var options: Options
    private lateinit var codeGenerator: CodeGenerator
    override lateinit var resolver: Resolver
    override lateinit var logger: KSPLogger

    override fun init(
        options: Map<String, String>,
        kotlinVersion: KotlinVersion,
        codeGenerator: CodeGenerator,
        logger: KSPLogger
    ) {
        this.options = Options.from(options, profiler)
        this.codeGenerator = codeGenerator
        this.logger = logger
    }

    override fun process(resolver: Resolver) {
        this.resolver = resolver

        val generator = InjectGenerator(this, options)
        val allScopeClasses = mutableSetOf<AstClass>()

        for (element in resolver.getSymbolsWithAnnotation(Component::class.qualifiedName!!)) {
            if (element !is KSClassDeclaration) continue
            val astClass = element.toAstClass()
            val scopedClass = astClass.scopeClass(messenger, options)

            val scopeType = scopedClass?.scopeType(options)
            if (scopeType != null) {
                allScopeClasses.addAll(scopedClasses(scopeType, resolver))
            }

            try {
                val file = generator.generate(astClass)
                file.writeTo(codeGenerator)
            } catch (e: FailedToGenerateException) {
                error(e.message.orEmpty(), e.element)
                // Continue so we can see all errors
                continue
            }
        }
    }

    private fun scopedClasses(scopeType: AstType, resolver: Resolver): List<AstClass> {
        return resolver.getSymbolsWithAnnotation(scopeType.toAstClass().asClassName().toString()).mapNotNull {
            (it as? KSClassDeclaration)?.toAstClass()
        }
    }

    override fun finish() {
        messenger.finalize()
    }

    private fun FileSpec.writeTo(codeGenerator: CodeGenerator) {
        codeGenerator.createNewFile(packageName, name).bufferedWriter().use {
            writeTo(it)
        }
    }
}