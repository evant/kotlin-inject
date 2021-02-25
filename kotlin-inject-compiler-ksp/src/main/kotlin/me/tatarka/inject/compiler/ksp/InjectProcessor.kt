package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.compiler.FailedToGenerateException
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.Options
import me.tatarka.inject.compiler.Profiler

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
        this.options = Options.from(options)
        this.codeGenerator = codeGenerator
        this.logger = logger
    }

    @Suppress("LoopWithTooManyJumpStatements")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        this.resolver = resolver

        profiler?.onStart()

        val generator = InjectGenerator(this, options)

        for (element in resolver.getSymbolsWithAnnotation(Component::class.qualifiedName!!)) {
            if (element !is KSClassDeclaration) continue
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

        profiler?.onStop()

        return emptyList()
    }

    override fun finish() {
        // ignore
    }
}