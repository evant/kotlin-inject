package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

fun TypeResultRef.generate() = result.generate()

fun TypeResult.generate(): CodeBlock {
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

fun TypeResult.Provider.generateInto(typeSpec: TypeSpec.Builder) {
    val codeBlock = CodeBlock.builder().apply {
        add("return ")
        add(result.generate())
        add("\n»")
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

private fun TypeResult.Provider.generate(): CodeBlock {
    // TODO: allow these to be generated at a local level.
    return CodeBlock.builder().apply {
    }.build()
}

@Suppress("LongMethod")
private fun TypeResult.Provides.generate(): CodeBlock {
    return CodeBlock.builder().apply {

        val changeScope = accessor != null && receiver != null

        if (accessor != null) {
            if (changeScope) {
                add("with(this@%L.%L)", className, accessor)
                beginControlFlow("")
            } else {
                add("%L.", accessor)
            }
        }

        if (receiver != null) {
            add(receiver.generate())
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
                add(param.generate())
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
        if (accessor != null) {
            add(
                "(%L as %T).",
                accessor,
                SCOPED_COMPONENT
            )
        }
        add("_scoped.get(%S)", key).beginControlFlow("")
        add(result.generate())
        add("\n")
        endControlFlow()
    }.build()
}

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