package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstConstructor
import me.tatarka.kotlin.ast.AstParam
import me.tatarka.kotlin.ast.AstProvider
import kotlin.reflect.KClass

class CreateGenerator(private val astProvider: AstProvider, private val options: Options) {

    fun create(element: AstClass, constructor: AstConstructor?, injectComponent: TypeSpec): List<FunSpec> {
        @Suppress("MaxLineLength")
        val companion = if (options.generateCompanionExtensions) {
            element.companion.also {
                if (it == null) {
                    astProvider.error(
                        """Missing companion for class: ${element.toClassName()}.
                            |When you have the option me.tatarka.inject.generateCompanionExtensions=true you must declare a companion option on the component class for the extension function to apply to.
                            |You can do so by adding 'companion object' to the class.
                        """.trimMargin(),
                            element
                    )
                }
            }
        } else {
            null
        }
        return mutableListOf<FunSpec>().apply {
            val typeName = element.type.toTypeName()
            val params = constructor?.parameters ?: emptyList()
            add(generateCreate(element, typeName, constructor, injectComponent, companion, params))
            val nonDefaultParams = constructor?.parameters?.filter { !it.hasDefault } ?: emptyList()
            if (params.size != nonDefaultParams.size) {
                add(generateCreate(element, typeName, constructor, injectComponent, companion, nonDefaultParams))
            }
        }
    }

    private fun generateCreate(
        element: AstClass,
        typeName: TypeName,
        constructor: AstConstructor?,
        injectComponent: TypeSpec,
        companion: AstClass?,
        params: List<AstParam>
    ): FunSpec {
        return FunSpec.builder("create")
            .apply {
                addModifiers(element.visibility.toKModifier())
                if (constructor != null) {
                    for (param in params) {
                        addParameter(param.toParameterSpec())
                    }
                }
                if (companion != null) {
                    receiver(companion.type.toTypeName())
                } else {
                    receiver(KClass::class.asClassName().plusParameter(typeName))
                }
            }
            .returns(typeName)
            .apply {
                val codeBlock = CodeBlock.builder()
                codeBlock.add("return %N(", injectComponent)
                if (constructor != null) {
                    params.forEachIndexed { i, parameter ->
                        if (i != 0) {
                            codeBlock.add(", ")
                        }
                        codeBlock.add("%L", parameter.name)
                    }
                }
                codeBlock.add(")")
                addCode(codeBlock.build())
            }
            .build()
    }
}