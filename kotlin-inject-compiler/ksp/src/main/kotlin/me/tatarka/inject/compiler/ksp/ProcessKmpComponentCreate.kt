package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.compiler.COMPONENT
import me.tatarka.inject.compiler.KmpComponentCreateGenerator
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstFunction
import me.tatarka.kotlin.ast.KSAstProvider

internal fun processKmpComponentCreate(
    element: KSFunctionDeclaration,
    provider: KSAstProvider,
    kmpComponentCreateFunctionsByComponentType: MutableMap<AstClass, MutableList<AstFunction>>
): Boolean = with(provider) {
    val astFunction = element.toAstFunction()
    val returnType = astFunction.returnType

    // the generated actual function will be annotated with KmpComponentCreate
    // KSP will process them as well so we need to ignore them
    if (astFunction.isActual && element.findExpects().firstOrNull() != null) return true
    if (!astFunction.validateIsExpect(provider)) return true
    if (returnType.isError) return false

    val returnTypeClass = returnType.resolvedType().toAstClass()
    if (!astFunction.validateReturnType(returnTypeClass, provider)) return true

    kmpComponentCreateFunctionsByComponentType.getOrPut(returnTypeClass, ::ArrayList).add(astFunction)

    true
}

internal fun generateKmpComponentCreateFiles(
    codeGenerator: CodeGenerator,
    generator: KmpComponentCreateGenerator,
    kmpComponentCreateFunctionsByComponentType: Map<AstClass, List<AstFunction>>
) {
    kmpComponentCreateFunctionsByComponentType.forEach { (componentType, kmpComponentCreateFunctions) ->
        val file = generator.generate(componentType, kmpComponentCreateFunctions)
        file.writeTo(codeGenerator, aggregating = true)
    }
}

private fun AstFunction.validateIsExpect(provider: KSAstProvider) = isExpect.also { isValid ->
    if (!isValid) {
        provider.error("$name should be an expect fun", this)
    }
}

private fun AstFunction.validateReturnType(returnTypeClass: AstClass, provider: KSAstProvider): Boolean =
    returnTypeClass
        .hasAnnotation(
            packageName = COMPONENT.packageName,
            simpleName = COMPONENT.simpleName
        )
        .also { isValid ->
            if (!isValid) {
                provider.error("$name's return type should be a type annotated with @Component", this)
            }
        }
