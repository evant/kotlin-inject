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
        astFunction: AstFunction,
        returnTypeClass: AstClass,
    ) = with(provider) {
        FileSpec.builder(
            packageName = astFunction.packageName,
            fileName = "KmpComponentCreate${returnTypeClass.name}",
        ).apply {
            addFunction(
                FunSpec
                    .builder(astFunction.name)
                    .addOriginatingElement(astFunction)
                    .apply {
                        astFunction.annotations.forEach { annotation ->
                            addAnnotation(annotation.toAnnotationSpec())
                        }

                        addModifiers(
                            astFunction.visibility.toKModifier(),
                            KModifier.ACTUAL,
                        )

                        astFunction.receiverParameterType?.toTypeName()?.let(::receiver)

                        for (param in astFunction.parameters) {
                            addParameter(param.name, param.type.toTypeName())
                        }

                        val funcParams = astFunction.parameters.joinToString { "%N" }
                        val funcParamsNames = astFunction.parameters.map { it.name }.toTypedArray()

                        val returnTypeCompanion = when {
                            options.generateCompanionExtensions -> returnTypeClass.companion?.type
                            else -> null
                        }

                        val returnTypeName = returnTypeClass.type.toTypeName()

                        val (createReceiver, createReceiverClassName) = when (returnTypeCompanion) {
                            null -> "%T::class" to returnTypeClass.toClassName()
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
        }.build()
    }
}
