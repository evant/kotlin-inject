package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.tags.TypeAliasTag

data class TypeResultGenerator(val options: Options, val changedScope: Boolean = false) {

    fun TypeResult.Provider.generateInto(typeSpec: TypeSpec.Builder) {
        val codeBlock = CodeBlock.builder().apply {
            val accessTypes = mutableMapOf<String, TypeName>()
            collectCheckAccessTypes(accessTypes)
            for ((accessor, type) in accessTypes) {
                addStatement("require(%L is %T)", accessor, type)
            }
            add("return ")
            add(result.generate())
        }.build()

        if (isProperty) {
            typeSpec.addProperty(
                PropertySpec.builder(name, returnType.asTypeName())
                    .apply {
                        if (isPrivate) addModifiers(KModifier.PRIVATE)
                        if (isOverride) addModifiers(KModifier.OVERRIDE)
                    }
                    .getter(FunSpec.getterBuilder().addCode(codeBlock).build()).build()
            )
        } else {
            typeSpec.addFunction(
                FunSpec.builder(name).returns(returnType.asTypeName())
                    .apply {
                        if (isPrivate) addModifiers(KModifier.PRIVATE)
                        if (isOverride) addModifiers(KModifier.OVERRIDE)
                        if (isSuspend) addModifiers(KModifier.SUSPEND)
                    }
                    .addCode(codeBlock).build()
            )
        }
    }

    private fun TypeResult.collectCheckAccessTypes(result: MutableMap<String, TypeName>) {
        if (this is TypeResult.Scoped && accessor.isNotEmpty()) {
            result[this.accessor] = SCOPED_COMPONENT
        }
        val children = children
        while (children.hasNext()) {
            children.next().result.collectCheckAccessTypes(result)
        }
    }

    private fun TypeResultRef.generate() = result.generate()

    private fun TypeResult.generate(): CodeBlock {
        return when (this) {
            is TypeResult.Provider -> generate()
            is TypeResult.Provides -> generate()
            is TypeResult.Scoped -> generate()
            is TypeResult.Constructor -> generate()
            is TypeResult.Container -> generate()
            is TypeResult.Function -> generate()
            is TypeResult.NamedFunction -> generate()
            is TypeResult.Object -> generate()
            is TypeResult.Arg -> generate()
            is TypeResult.Lazy -> generate()
            is TypeResult.LateInit -> generate()
            is TypeResult.LocalVar -> generate()
        }
    }

    private fun TypeResult.Provider.generate(): CodeBlock {
        // TODO: allow these to be generated at a local level.
        return CodeBlock.builder().apply {
        }.build()
    }

    @Suppress("LongMethod", "NestedBlockDepth")
    private fun TypeResult.Provides.generate(): CodeBlock {
        return CodeBlock.builder().apply {

            val changeScope = accessor.isNotEmpty() && receiver != null

            if (accessor.isNotEmpty()) {
                if (changeScope) {
                    add("with(")
                    if (changedScope) {
                        add("this@%L.", className)
                    }
                    add("%L)", accessor)
                    beginControlFlow("")
                } else {
                    if (changedScope) {
                        add("this@%L.", className)
                    }
                    add("%L.", accessor)
                }
            }

            if (receiver != null) {
                with(copy(changedScope = changeScope)) {
                    add(receiver.generate())
                }
                add(".")
            }

            if (isProperty) {
                add("%N", methodName)
            } else {
                add("%N(", methodName)
                parameters.forEachIndexed { i, param ->
                    if (i != 0) {
                        add(",")
                    }
                    with(copy(changedScope = changeScope)) {
                        add(param.generate())
                    }
                }
                add(")")
            }

            if (changeScope) {
                add("\n")
                endControlFlow()
            }
        }.build()
    }

    private fun TypeResult.Constructor.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            add("%T(", type.asTypeName())
            parameters.forEachIndexed { i, param ->
                if (i != 0) {
                    add(",")
                }
                add(param.generate())
            }
            add(")")
        }.build()
    }

    private fun TypeResult.Scoped.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            if (accessor.isNotEmpty()) {
                add("%L.", accessor)
            }
            add("_scoped.get(")
            if (key.qualifier != null) {
                add("%S + ", key.qualifier)
            }
            addTypeName(key.type.asTypeName())
            add(")")
            beginControlFlow("")
            add(result.generate())
            add("\n")
            endControlFlow()
        }.build()
    }

    private fun CodeBlock.Builder.addTypeName(typeName: TypeName) {
        if (options.useClassReferenceForScopeAccess) {
            when (typeName) {
                is ClassName -> if (typeName.isTypeAlias) {
                    add("%S", typeName)
                } else {
                    add("%T::class.java.name", typeName)
                }
                is ParameterizedTypeName -> {
                    addTypeName(typeName.rawType)
                    for (arg in typeName.typeArguments) {
                        add("+")
                        addTypeName(arg)
                    }
                }
                is LambdaTypeName -> {
                    val functionName = if (typeName.isSuspending) {
                        ClassName("kotlin.coroutines", "SuspendFunction${typeName.parameters.size}")
                    } else {
                        ClassName("kotlin", "Function${typeName.parameters.size}")
                    }
                    add("%T::class.java.name", functionName)
                    for (param in typeName.parameters) {
                        add("+")
                        addTypeName(param.type)
                        add("+%S", ";")
                    }
                    add("+")
                    addTypeName(typeName.returnType)
                }
                else -> add("%S", typeName)
            }
        } else {
            add("%S", typeName)
        }
    }

    private val TypeName.isTypeAlias: Boolean
        get() = tag(TypeAliasTag::class) != null

    private fun TypeResult.Container.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            add("$creator(")
            args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(", ")
                }
                add(arg.generate())
            }
            add(")")
        }.build()
    }

    private fun TypeResult.Function.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("")
            args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(",")
                }
                add(" %L", arg)
            }
            if (args.isNotEmpty()) {
                add(" ->")
            }
            add(result.generate())
            endControlFlow()
        }.build()
    }

    private fun TypeResult.NamedFunction.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("")
            args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(",")
                }
                add(" %L", arg)
            }
            if (args.isNotEmpty()) {
                add(" ->")
            }

            add("%M(", name)
            parameters.forEachIndexed { i, param ->
                if (i != 0) {
                    add(",")
                }
                add(param.generate())
            }
            add(")")

            endControlFlow()
        }.build()
    }

    private fun TypeResult.Object.generate(): CodeBlock {
        return CodeBlock.builder().add("%T", type.asTypeName()).build()
    }

    private fun TypeResult.Arg.generate(): CodeBlock {
        return CodeBlock.of(name)
    }

    private fun TypeResult.LocalVar.generate(): CodeBlock {
        return CodeBlock.of(name)
    }

    private fun TypeResult.Lazy.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("lazy")
            add(result.generate())
            add("\n")
            endControlFlow()
        }.build()
    }

    private fun TypeResult.LateInit.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("run")
            addStatement("lateinit var %N: %T", name, result.key.type.asTypeName())
            add(result.generate())
            beginControlFlow(".also")
            addStatement("%N = it", name)
            endControlFlow()
            endControlFlow()
        }.build()
    }
}