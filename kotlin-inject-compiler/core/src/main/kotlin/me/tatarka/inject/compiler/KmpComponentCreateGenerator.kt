package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstFunction
import me.tatarka.kotlin.ast.AstProvider

class KmpComponentCreateGenerator(
    private val provider: AstProvider,
    private val options: Options,
) {
    fun generate(
        componentClass: AstClass,
        kmpComponentCreateFunctions: List<AstFunction>,
    ) = with(provider) {
        FileSpec.builder(
            packageName = componentClass.packageName,
            fileName = "KmpComponentCreate${componentClass.name}",
        ).apply {
            kmpComponentCreateFunctions.forEach { kmpComponentCreateFunction ->
                addFunction(
                    FunSpec
                        .builder(kmpComponentCreateFunction.name)
                        .addOriginatingElement(kmpComponentCreateFunction)
                        .apply {
                            kmpComponentCreateFunction.annotations.forEach { annotation ->
                                addAnnotation(annotation.toAnnotationSpec())
                            }

                            addModifiers(
                                kmpComponentCreateFunction.visibility.toKModifier(),
                                KModifier.ACTUAL,
                            )

                            kmpComponentCreateFunction.receiverParameterType?.toTypeName()?.let(::receiver)

                            for (param in kmpComponentCreateFunction.parameters) {
                                addParameter(param.name, param.type.toTypeName())
                            }

                            val funcParams = kmpComponentCreateFunction.parameters.joinToString { "%N" }
                            val funcParamsNames = kmpComponentCreateFunction.parameters.map { it.name }.toTypedArray()

                            val returnTypeCompanion = when {
                                options.generateCompanionExtensions -> componentClass.companion?.type
                                else -> null
                            }

                            val returnTypeName = componentClass.type.toTypeName()

                            val (createReceiver, createReceiverClassName) = when (returnTypeCompanion) {
                                null -> "%T::class" to componentClass.toClassName()
                                else -> "%T" to returnTypeCompanion.toAstClass().toClassName()
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
