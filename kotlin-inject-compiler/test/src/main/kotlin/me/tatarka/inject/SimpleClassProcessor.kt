package me.tatarka.inject

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

class SimpleClassProcessor(
    private val targetInterface: String,
    private val generatedClass: String,
    private val generator: CodeGenerator
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        for (
        declaration in resolver.getNewFiles()
            .flatMap { it.declarations }
            .filter { it.simpleName.asString() == targetInterface }
        ) {
            generator.createNewFile(
                Dependencies(false, declaration.containingFile!!),
                declaration.packageName.asString(),
                generatedClass
            ).bufferedWriter().use { file ->
                file.write(
                    """
                    interface $generatedClass : $targetInterface
                    """.trimIndent()
                )
            }
        }

        return emptyList()
    }

    class Provider(private val targetInterface: String, private val generatedClass: String) : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
            SimpleClassProcessor(targetInterface, generatedClass, environment.codeGenerator)
    }
}
