package me.tatarka.inject.compiler.ksp

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.compiler.FailedToGenerateException
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.Options
import me.tatarka.inject.compiler.ast.AstClass
import me.tatarka.inject.compiler.ast.KSAstProvider
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSType
import java.io.FileWriter

class InjectProcessor : SymbolProcessor, KSAstProvider {

    private lateinit var options: Options
    private lateinit var codeGenerator: CodeGenerator
    override lateinit var resolver: Resolver

    override fun init(options: Map<String, String>, kotlinVersion: KotlinVersion, codeGenerator: CodeGenerator) {
        this.options = Options.from(options)
        this.codeGenerator = codeGenerator
    }

    override fun process(resolver: Resolver) {
        this.resolver = resolver
        for (element in resolver.getSymbolsWithAnnotation(Component::class.qualifiedName!!)) {
            if (element !is KSClassDeclaration) continue
            val generator = InjectGenerator(this, options)

            val scopeType = element.scopeType()
            val scopedInjects = scopeType?.let { scopedInjects(it, resolver) } ?: emptyList()

//            try {
                val astClass = element.toAstClass()
                val file = generator.generate(astClass, scopedInjects)
                FileWriter(codeGenerator.createNewFile(file.packageName, file.name)).buffered().use {
                   file.writeTo(it)
                }
//            } catch (e: FailedToGenerateException) {
//                // Continue so we can see all errors
//                continue
//            }
        }
    }

    private fun scopedInjects(scopeType: KSType, resolver: Resolver): List<AstClass> {
        return resolver.getSymbolsWithAnnotation(scopeType.declaration.qualifiedName!!.asString()).mapNotNull {
            // skip component itself, we only want @Inject's annotated with the scope
            if (it.getAnnotation(Component::class) != null) {
                null
            } else {
                (it as? KSClassDeclaration)?.toAstClass()
            }
        }
    }

    override fun finish() {
    }
}