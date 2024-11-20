package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.compiler.COMPONENT
import me.tatarka.inject.compiler.ComponentClassInfo
import me.tatarka.inject.compiler.KmpComponentCreateFunctionInfo
import me.tatarka.inject.compiler.KmpComponentCreateGenerator
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstFunction
import me.tatarka.kotlin.ast.KSAstProvider

private typealias KmpComponentCreateFunctionsByComponentType =
    MutableMap<ComponentClassInfo, MutableList<KmpComponentCreateFunctionInfo>>

internal fun processKmpComponentCreate(
    element: KSFunctionDeclaration,
    provider: KSAstProvider,
    kmpComponentCreateFunctionsByComponentType: KmpComponentCreateFunctionsByComponentType
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

    val returnTypeClassInfo = ComponentClassInfo(
        packageName = returnTypeClass.packageName,
        name = returnTypeClass.name,
        companionClassName = returnTypeClass.companion?.type?.toAstClass()?.toClassName(),
        typeName = returnTypeClass.type.toTypeName(),
        className = returnTypeClass.toClassName(),
    )

    val functionInfo = KmpComponentCreateFunctionInfo(
        name = astFunction.name,
        annotations = astFunction.annotations.map { it.toAnnotationSpec() }.toList(),
        visibility = astFunction.visibility.toKModifier(),
        receiverParameterType = astFunction.receiverParameterType?.toTypeName(),
        parameters = astFunction.parameters.map { it.name to it.type.toTypeName() },
        parametersTemplate = astFunction.parameters.joinToString { "%N" },
        parameterNames = astFunction.parameters.map { it.name },
    )

    kmpComponentCreateFunctionsByComponentType.getOrPut(returnTypeClassInfo, ::ArrayList).add(functionInfo)

    true
}

internal fun generateKmpComponentCreateFiles(
    codeGenerator: CodeGenerator,
    generator: KmpComponentCreateGenerator,
    kmpComponentCreateFunctionsByComponentType: Map<ComponentClassInfo, List<KmpComponentCreateFunctionInfo>>
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
