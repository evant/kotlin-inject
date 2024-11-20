package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeName
import me.tatarka.kotlin.ast.AstProvider

data class ComponentClassInfo(
    val packageName: String,
    val name: String,
    val companionClassName: ClassName?,
    val typeName: TypeName,
    val className: ClassName,
)

data class KmpComponentCreateFunctionInfo(
    val name: String,
    val annotations: List<AnnotationSpec>,
    val visibility: KModifier,
    val receiverParameterType: TypeName?,
    val parameters: List<Pair<String, TypeName>>,
    val parametersTemplate: String,
    val parameterNames: List<String>,
)

class KmpComponentCreateGenerator(
    private val provider: AstProvider,
    private val options: Options,
) {
    fun generate(
        componentClass: ComponentClassInfo,
        kmpComponentCreateFunctions: List<KmpComponentCreateFunctionInfo>,
    ) = with(provider) {
        FileSpec.builder(
            packageName = componentClass.packageName,
            fileName = "KmpComponentCreate${componentClass.name}",
        ).apply {
            kmpComponentCreateFunctions.forEach { kmpComponentCreateFunction ->
                addFunction(
                    FunSpec
                        .builder(kmpComponentCreateFunction.name)
//                        .addOriginatingElement(kmpComponentCreateFunction)
                        .apply {
                            kmpComponentCreateFunction.annotations.forEach { annotation ->
                                addAnnotation(annotation)
                            }

                            addModifiers(
                                kmpComponentCreateFunction.visibility,
                                KModifier.ACTUAL,
                            )

                            kmpComponentCreateFunction.receiverParameterType?.let(::receiver)

                            for (param in kmpComponentCreateFunction.parameters) {
                                val (name, typeName) = param
                                addParameter(name, typeName)
                            }

                            val funcParams = kmpComponentCreateFunction.parameters.joinToString { "%N" }
                            val funcParamsNames = kmpComponentCreateFunction.parameterNames.toTypedArray()

                            val returnTypeCompanion = when {
                                options.generateCompanionExtensions -> componentClass.companionClassName
                                else -> null
                            }

                            val returnTypeName = componentClass.typeName

                            val (createReceiver, createReceiverClassName) = when (returnTypeCompanion) {
                                null -> "%T::class" to componentClass.className
                                else -> "%T" to returnTypeCompanion
                            }
                            addCode(
                                CodeBlock.of(
                                    "return $createReceiver.create($funcParams)",
                                    createReceiverClassName,
                                    *funcParamsNames
                                ),
                            )

                            returns(returnTypeName)
                        }.build(),
                )
            }
        }.build()
    }
}
