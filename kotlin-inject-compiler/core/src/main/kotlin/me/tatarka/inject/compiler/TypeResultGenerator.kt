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

data class TypeResultGenerator(val options: Options, val implicitAccessor: Accessor = Accessor.Empty) {

    fun TypeResult.Provider.generateInto(typeSpec: TypeSpec.Builder) {
        val codeBlock = CodeBlock.builder().apply {
            val accessTypes = mutableMapOf<Accessor, TypeName>()
            collectCheckAccessTypes(accessTypes)
            for ((accessor, type) in accessTypes) {
                addStatement("require(%L is %T)", accessor, type)
            }
            add("return·")
            add(result.generate())
        }.build()

        if (isProperty) {
            typeSpec.addProperty(
                PropertySpec.builder(name, returnType.toTypeName())
                    .apply {
                        if (isPrivate) addModifiers(KModifier.PRIVATE)
                        if (isOverride) addModifiers(KModifier.OVERRIDE)
                    }
                    .getter(FunSpec.getterBuilder().addCode(codeBlock).build()).build()
            )
        } else {
            typeSpec.addFunction(
                FunSpec.builder(name).returns(returnType.toTypeName())
                    .apply {
                        if (isPrivate) addModifiers(KModifier.PRIVATE)
                        if (isOverride) addModifiers(KModifier.OVERRIDE)
                        if (isSuspend) addModifiers(KModifier.SUSPEND)
                    }
                    .addCode(codeBlock).build()
            )
        }
    }

    private fun TypeResult.collectCheckAccessTypes(result: MutableMap<Accessor, TypeName>) {
        // Only for accessors of size one, as additional indirections may cause smart casting not to work.
        if (this is TypeResult.Scoped && accessor.size == 1) {
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

    @Suppress("LongMethod", "NestedBlockDepth", "ComplexMethod")
    private fun TypeResult.Provides.generate(): CodeBlock {
        return CodeBlock.builder().apply {

            val accessorInScope = implicitAccessor.resolve(accessor)
            val changeScope = receiver != null && accessorInScope.isNotEmpty()

            if (accessorInScope.isNotEmpty()) {
                if (changeScope) {
                    add("with(")
                    if (implicitAccessor.isNotEmpty() && accessor == accessorInScope) {
                        add("this@%L.", className)
                    }
                    add("%L)", accessorInScope)
                    beginControlFlow("")
                } else {
                    if (implicitAccessor.isNotEmpty() && accessor == accessorInScope) {
                        add("this@%L.", className)
                    }
                    add("%L.", accessorInScope)
                }
            }

            if (receiver != null) {
                with(if (changeScope) copy(implicitAccessor = accessor) else this@TypeResultGenerator) {
                    add(receiver.generate())
                }
                add(".")
            }

            if (isProperty) {
                add("%N", methodName)
            } else {
                add("%N(", methodName)
                if (parameters.isNotEmpty()) {
                    add("\n⇥")
                }
                parameters.entries.forEachIndexed { i, (paramName, param) ->
                    if (i != 0) {
                        add(",\n")
                    }
                    with(if (changeScope) copy(implicitAccessor = accessor) else this@TypeResultGenerator) {
                        add("$paramName = ")
                        add(param.generate())
                    }
                }
                if (parameters.isNotEmpty()) {
                    add("\n⇤")
                }
                add(")")
            }

            if (changeScope) {
                // don't use endControlFlow() because it emits a newline after the closing brace
                add("\n⇤}")
            }
        }.build()
    }

    private fun TypeResult.Constructor.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            add("%T(", type.toTypeName())
            if (parameters.isNotEmpty()) {
              add("\n⇥")
              val isNamedArgumentsSupported = supportsNamedArguments
              parameters.entries.forEachIndexed { i, (paramName, param) ->
                if (i != 0) {
                  add(",\n")
                }
                when {
                  isNamedArgumentsSupported -> {
                    add("$paramName = ")
                    add(param.generate())
                  }
                  else -> add(param.generate())
                }
              }
              add("\n⇤")
            }
            add(")")
        }.build()
    }

    private fun TypeResult.Scoped.generate(): CodeBlock {
        fun TypeResultRef.detectLeaks(accessorInScope: Accessor) {
            val result = result
            if (result is TypeResult.Provides) {
                val leaked = result.parameters.mapNotNull { param ->
                    param.value.result as? TypeResult.Provides
                }.any { param ->
                    param.accessor.components.any { it != accessorInScope.toString() }
                }

                if (leaked) {
                    throw FailedToGenerateException("Leak detected")
                }
            }
        }

        return CodeBlock.builder().apply {
            val accessorInScope = implicitAccessor.resolve(accessor)
            if (accessor.size > 1) {
                if (accessorInScope.isNotEmpty()) {
                    add("(%L as %T).", accessorInScope, SCOPED_COMPONENT)
                } else {
                    add("(this as %T).", SCOPED_COMPONENT)
                }
            } else if (accessorInScope.isNotEmpty()) {
                result.detectLeaks(accessorInScope)
                add("%L.", accessorInScope)
            }
            add("_scoped.get(")
            if (key.qualifier != null) {
                add("%S + ", key.qualifier)
            }
            addTypeName(key.type.toTypeName())
            add(")")
            beginControlFlow("")
            add(result.generate())
            // don't use endControlFlow() because it emits a newline after the closing brace
            add("\n⇤}")
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
            add("\n⇥")
            args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(",\n")
                }
                add(arg.generate())
            }
            add("\n⇤")
            add(")")
        }.build()
    }

    private fun TypeResult.Function.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            // don't use beginControlFlow() so the arg list can be kept on the same line
            add("{")
            args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(",")
                }
                add(" %L", arg)
            }
            if (args.isNotEmpty()) {
                add(" ->")
            }
            add("\n⇥")
            add(result.generate())
            // don't use endControlFlow() because it emits a newline after the closing brace
            add("\n⇤}")
        }.build()
    }

    private fun TypeResult.NamedFunction.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            // don't use beginControlFlow() so the arg list can be kept on the same line
            add("{")
            args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(",")
                }
                add(" %L", arg)
            }
            if (args.isNotEmpty()) {
                add(" ->")
            }
            add("\n⇥")

            add("%M(", name)
            if (parameters.isNotEmpty()) {
                add("\n⇥")
            }
            parameters.entries.forEachIndexed { i, (paramName, param) ->
                if (i != 0) {
                    add(",\n")
                }
                add("$paramName = ")
                add(param.generate())
            }
            if (parameters.isNotEmpty()) {
                add("\n⇤")
            }
            add(")")

            // don't use endControlFlow() because it emits a newline after the closing brace
            add("\n⇤}")
        }.build()
    }

    private fun TypeResult.Object.generate(): CodeBlock {
        return CodeBlock.builder().add("%T", type.toTypeName()).build()
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
            // don't use endControlFlow() because it emits a newline after the closing brace
            add("\n⇤}")
        }.build()
    }

    private fun TypeResult.LateInit.generate(): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("run")
            addStatement("lateinit var %N: %T", name, result.key.type.toTypeName())
            add(result.generate())
            beginControlFlow(".also")
            addStatement("%N = it", name)
            endControlFlow()
            endControlFlow()
        }.build()
    }
}