package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.asClassName
import me.tatarka.inject.annotations.TargetComponentAccessor
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstFunction
import me.tatarka.kotlin.ast.AstProvider
import kotlin.reflect.KClass

class TargetComponentAccessorGenerator(
    private val provider: AstProvider,
    private val options: Options,
) {
    fun generate(
        astFunction: AstFunction,
        returnTypeClass: AstClass,
    ) = with(provider) {
        FileSpec.builder(
            packageName = astFunction.packageName,
            fileName = "Target${returnTypeClass.name}Accessor",
        ).apply {
            addFunction(
                FunSpec
                    .builder(astFunction.name)
                    .addOriginatingElement(astFunction)
                    .apply {
                        addAnnotation(TargetComponentAccessor::class)
                        astFunction.optInAnnotation()?.let(::addAnnotation)

                        addModifiers(
                            astFunction.visibility.toKModifier(),
                            KModifier.ACTUAL,
                        )

                        astFunction.receiverParameterType?.toTypeName()?.let(::receiver)

                        for (param in astFunction.parameters) {
                            addParameter(param.name, param.type.toTypeName())
                        }

                        val funcParams = astFunction.parameters.joinToString { it.name }

                        val returnTypeCompanion = when {
                            options.generateCompanionExtensions -> returnTypeClass.companion?.type
                            else -> null
                        }

                        val returnTypeName = returnTypeClass.type.toTypeName()

                        addCode(
                            CodeBlock.of(
                                "return %T.create($funcParams)",
                                when (returnTypeCompanion) {
                                    null -> KClass::class.asClassName().plusParameter(returnTypeName)
                                    else -> returnTypeCompanion
                                },
                            ),
                        )

                        returns(returnTypeName)
                    }.build(),
            )
        }.build()
    }
}
