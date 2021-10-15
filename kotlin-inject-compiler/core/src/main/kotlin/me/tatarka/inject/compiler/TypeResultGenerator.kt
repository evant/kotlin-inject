package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

fun TypeResultRef.generate(changedScope: Boolean = false) = result.generate(changedScope)

fun TypeResult.generate(changedScope: Boolean): CodeBlock {
    return when (this) {
        is TypeResult.Provider -> generate()
        is TypeResult.Provides -> generate(changedScope)
        is TypeResult.Scoped -> generate(changedScope)
        is TypeResult.Constructor -> generate(changedScope)
        is TypeResult.Container -> generate(changedScope)
        is TypeResult.Function -> generate(changedScope)
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

@Suppress("LongMethod", "NestedBlockDepth")
private fun TypeResult.Provides.generate(changedScope: Boolean): CodeBlock {
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
            add(receiver.generate(changeScope))
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
                add(param.generate(changeScope))
            }
            add(")")
        }

        if (changeScope) {
            add("\n")
            endControlFlow()
        }
    }.build()
}

private fun TypeResult.Constructor.generate(changedScope: Boolean): CodeBlock {
    return CodeBlock.builder().apply {
        add("%T(", type.asTypeName())
        parameters.forEachIndexed { i, param ->
            if (i != 0) {
                add(",")
            }
            add(param.generate(changedScope))
        }
        add(")")
    }.build()
}

private fun TypeResult.Scoped.generate(changedScope: Boolean): CodeBlock {
    return CodeBlock.builder().apply {
        if (accessor.isNotEmpty()) {
            add(
                "(%L as %T).",
                accessor,
                SCOPED_COMPONENT
            )
        }
        add("_scoped.get(%S)", key).beginControlFlow("")
        add(result.generate(changedScope))
        add("\n")
        endControlFlow()
    }.build()
}

private fun TypeResult.Container.generate(changedScope: Boolean): CodeBlock {
    return CodeBlock.builder().apply {
        add("$creator(")
        args.forEachIndexed { index, arg ->
            if (index != 0) {
                add(", ")
            }
            add(arg.generate(changedScope))
        }
        add(")")
    }.build()
}

private fun TypeResult.Function.generate(changedScope: Boolean): CodeBlock {
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
        add(result.generate(changedScope))
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