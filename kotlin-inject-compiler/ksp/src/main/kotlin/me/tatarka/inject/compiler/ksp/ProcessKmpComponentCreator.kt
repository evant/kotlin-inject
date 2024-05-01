package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.compiler.COMPONENT
import me.tatarka.inject.compiler.KmpComponentCreatorGenerator
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstFunction
import me.tatarka.kotlin.ast.KSAstProvider

internal fun processKmpComponentCreator(
    element: KSFunctionDeclaration,
    provider: KSAstProvider,
    codeGenerator: CodeGenerator,
    kmpComponentCreatorGenerator: KmpComponentCreatorGenerator,
): Boolean = with(provider) {
    val astFunction = element.toAstFunction()
    val returnType = astFunction.returnType

    // the generated actual function will be annotated with KmpComponentCreator
    // KSP will process them as well so we need to ignore them
    if (astFunction.isActual && element.findExpects().firstOrNull() != null) return true
    if (!astFunction.validateIsExpect(provider)) return true
    if (returnType.isError) return false

    val returnTypeClass = returnType.resolvedType().toAstClass()
    if (!astFunction.validateReturnType(returnTypeClass, provider)) return true

    process(astFunction, returnTypeClass, codeGenerator, kmpComponentCreatorGenerator)
    true
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

private fun process(
    astFunction: AstFunction,
    returnTypeClass: AstClass,
    codeGenerator: CodeGenerator,
    generator: KmpComponentCreatorGenerator,
) {
    val file = generator.generate(astFunction, returnTypeClass)
    file.writeTo(codeGenerator, aggregating = false)
}
