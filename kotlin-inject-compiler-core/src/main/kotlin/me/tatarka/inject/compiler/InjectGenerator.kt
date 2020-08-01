package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope
import kotlin.reflect.KClass

class InjectGenerator(provider: AstProvider, private val options: Options) :
    AstProvider by provider {

    fun generate(astClass: AstClass, scopedClasses: Collection<AstClass> = emptyList()): FileSpec {
        if (AstModifier.ABSTRACT !in astClass.modifiers) {
            throw FailedToGenerateException("@Component class: $astClass must be abstract", astClass)
        } else if (AstModifier.PRIVATE in astClass.modifiers) {
            throw FailedToGenerateException("@Component class: $astClass must not be private", astClass)
        }

        val constructor = astClass.primaryConstructor

        val scopedInjects = scopedClasses.filter { it.hasAnnotation<Inject>() }

        val injectComponent = generateInjectComponent(astClass, constructor, scopedInjects)
        val createFunction = generateCreate(astClass, constructor, injectComponent)

        return FileSpec.builder(astClass.packageName, "Inject${astClass.name}")
            .addType(injectComponent)
            .addFunction(createFunction)
            .build()
    }

    fun generateScopedInterfaces(scopedClasses: Collection<AstClass>): List<FileSpec> {
        val scopedInjects = scopedClasses.filter { it.hasAnnotation<Inject>() }
        val scopedInterfaces = scopedClasses.filter { !it.hasAnnotation<Inject>() && !it.hasAnnotation<Component>() }
        return scopedInterfaces.map {
            FileSpec.builder(it.packageName, "Inject${it.name}")
                .addType(generateScopedInterface(it, scopedInjects))
                .build()
        }
    }

    private fun generateInjectComponent(
        astClass: AstClass,
        constructor: AstConstructor?,
        scopedInjects: Collection<AstClass>
    ): TypeSpec {
        val context = collectTypes(astClass, scopedInjects, isComponent = true)

        return TypeSpec.classBuilder("Inject${astClass.name}")
            .addOriginatingElement(astClass)
            .superclass(astClass.asClassName())
            .apply {
                if (context.scopeInterface != null) {
                    addSuperinterface(
                        ClassName(
                            context.scopeInterface.packageName,
                            "Inject${context.scopeInterface.name}"
                        )
                    )
                }

                if (constructor != null) {
                    val funSpec = FunSpec.constructorBuilder()
                    for (parameter in constructor.parameters) {
                        val p = parameter.asParameterSpec()
                        funSpec.addParameter(p)
                        addSuperclassConstructorParameter("%N", p)
                    }
                    primaryConstructor(funSpec.build())
                }

                try {
                    for (type in context.collector.scoped) {
                        val codeBlock = CodeBlock.builder()
                        codeBlock.add("lazy { ")
                        codeBlock.add(provide(TypeKey(type), context.withoutScoped(type)))
                        codeBlock.add(" }")

                        addProperty(
                            PropertySpec.builder(
                                type.asElement().simpleName.asScopedProp(),
                                type.asTypeName()
                            ).apply {
                                if (context.scopeInterface != null) {
                                    addModifiers(KModifier.OVERRIDE)
                                }
                            }.delegate(codeBlock.build())
                                .build()
                        )
                    }

                    astClass.visitInheritanceChain { parentClass ->
                        for (method in parentClass.methods) {
                            if (method.isProvider()) {
                                val codeBlock = CodeBlock.builder()
                                codeBlock.add("return ")
                                val returnType = method.returnTypeFor(astClass)

                                codeBlock.add(provide(key = TypeKey(returnType), context = context, source = method))

                                addProperty(
                                    PropertySpec.builder(
                                        method.name,
                                        returnType.asTypeName(),
                                        KModifier.OVERRIDE
                                    )
                                        .getter(
                                            FunSpec.getterBuilder()
                                                .addCode(codeBlock.build())
                                                .build()
                                        )
                                        .build()
                                )
                            }
                        }
                    }
                } catch (e: FailedToGenerateException) {
                    error(e.message.orEmpty(), e.element)
                    // Create a stub component to prevent extra compile errors, the original one will still be reported.
                }
            }
            .build()
    }

    private fun generateScopedInterface(
        astClass: AstClass,
        scopedInjects: Collection<AstClass>
    ): TypeSpec {
        val context = collectTypes(astClass, scopedInjects, isComponent = false)

        return TypeSpec.interfaceBuilder("Inject${astClass.name}")
            .addOriginatingElement(astClass)
            .apply {
                for (type in context.collector.scoped) {
                    addProperty(
                        PropertySpec.builder(
                            type.asElement().simpleName.asScopedProp(),
                            type.asTypeName()
                        ).build()
                    )
                }
            }.build()
    }

    private fun generateCreate(
        element: AstClass,
        constructor: AstConstructor?,
        injectComponent: TypeSpec
    ): FunSpec {
        return FunSpec.builder("create")
            .apply {
                if (constructor != null) {
                    addParameters(ParameterSpec.parametersOf(constructor))
                }
                if (options.generateCompanionExtensions) {
                    val companion = element.companion
                    if (companion != null) {
                        receiver(companion.type.asTypeName())
                    } else {
                        error(
                            """Missing companion for class: ${element.asClassName()}.
                            |When you have the option me.tatarka.inject.generateCompanionExtensions=true you must declare a companion option on the component class for the extension function to apply to.
                            |You can do so by adding 'companion object' to the class.
                        """.trimMargin(), element
                        )
                    }
                } else {
                    receiver(KClass::class.asClassName().plusParameter(element.type.asTypeName()))
                }
            }
            .returns(element.type.asTypeName())
            .apply {
                val codeBlock = CodeBlock.builder()
                codeBlock.add("return %N(", injectComponent)
                if (constructor != null) {
                    constructor.parameters.forEachIndexed { i, parameter ->
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

    private fun collectTypes(
        astClass: AstClass,
        scopedInjects: Collection<AstClass>,
        isComponent: Boolean
    ): Context {
        val typeCollector = TypeCollector(
            provider = this,
            astClass = astClass,
            isComponent = isComponent,
            scopedInjects = scopedInjects
        )
        val elementScopeClass = astClass.scopeClass(messenger)
        val scopeFromParent = elementScopeClass != astClass
        return Context(
            source = astClass,
            collector = typeCollector,
            scopeInterface = if (scopeFromParent) elementScopeClass else null
        )
    }

    private fun provide(
        key: TypeKey,
        context: Context,
        source: AstElement? = null,
        find: (TypeKey) -> Result? = { context.find(it) }
    ): CodeBlock {
        val result = find(key)
            ?: throw FailedToGenerateException(
                "Cannot find an @Inject constructor or provider for: $key",
                source ?: context.source
            )
        return when (result) {
            is Result.Provides -> provideProvides(result, context)
            is Result.Scoped -> provideScoped(key, result)
            is Result.Constructor -> provideConstructor(result.constructor, context)
            is Result.Container -> provideContainer(result, context)
            is Result.Function -> provideFunction(result, context)
            is Result.NamedFunction -> provideNamedFunction(result, context)
            is Result.Arg -> provideArg(result)
            is Result.Lazy -> provideLazy(result, context)
        }
    }

    private fun provideProvides(
        providesResult: Result.Provides,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()

        val method = providesResult.method
        val receiverParamType = method.receiverParameterType
        val changeScope = providesResult.name != null && receiverParamType != null

        if (providesResult.name != null) {
            if (changeScope) {
                codeBlock.add("with(%L)", providesResult.name)
                codeBlock.beginControlFlow(" {")
            } else {
                codeBlock.add("%L.", providesResult.name)
            }
        }

        when (method) {
            is AstProperty -> {
                if (receiverParamType != null) {
                    codeBlock.add(provide(key = TypeKey(receiverParamType), context = context, source = method))
                    codeBlock.add(".")
                }
                codeBlock.add("%N", method.name)
            }
            is AstFunction -> {
                if (receiverParamType != null) {
                    codeBlock.add(provide(key = TypeKey(receiverParamType), context = context, source = method))
                    codeBlock.add(".")
                }
                codeBlock.add("%N(", method.name)
                val size = method.parameters.size
                method.parameters.forEachIndexed { i, param ->
                    if (i != 0) {
                        codeBlock.add(",")
                    }
                    codeBlock.add(provide(key = TypeKey(param.type), context = context, source = param) { context.findWithIndex(it, i, size) })
                }
                codeBlock.add(")")
            }
        }

        if (changeScope) {
            codeBlock.endControlFlow()
        }

        return codeBlock.build()
    }

    private fun provideConstructor(
        constructor: AstConstructor,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        codeBlock.add("%T(", constructor.type.asTypeName())
        val size = constructor.parameters.size
        constructor.parameters.forEachIndexed { i, param ->
            if (i != 0) {
                codeBlock.add(",")
            }
            codeBlock.add(provide(key = TypeKey(param.type), context = context, source = param) { context.findWithIndex(it, i, size) })
        }
        codeBlock.add(")")
        return codeBlock.build()
    }

    private fun provideScoped(key: TypeKey, result: Result.Scoped): CodeBlock {
        val codeBlock = CodeBlock.builder()
        if (result.name != null) {
            codeBlock.add("(%L as Inject%N).", result.name, result.astClass.name)
        }
        codeBlock.add("%N", key.type.asElement().simpleName.asScopedProp())
        return codeBlock.build()
    }

    private fun provideContainer(containerResult: Result.Container, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            add("${containerResult.creator}(")
            containerResult.methods.forEachIndexed { index, method ->
                if (index != 0) {
                    add(", ")
                }
                add(
                    provideProvides(
                        Result.Provides(
                            null,
                            method
                        ), context
                    )
                )
            }
            add(")")
        }.build()
    }

    private fun provideFunction(result: Result.Function, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("{")
            val argList = mutableListOf<Pair<AstType, String>>()
            result.args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(",")
                }
                val name = "arg$index"
                argList.add(arg to name)
                add(" %L", name)
            }
            if (result.args.isNotEmpty()) {
                add(" ->")
            }

            add(provide(result.key, context.withArgs(argList)))
            endControlFlow()
        }.build()
    }

    private fun provideNamedFunction(result: Result.NamedFunction, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("{")
            val argList = mutableListOf<Pair<AstType, String>>()
            result.args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(",")
                }
                val name = "arg$index"
                argList.add(arg to name)
                add(" %L", name)
            }
            if (result.args.isNotEmpty()) {
                add(" ->")
            }

            val context = context.withArgs(argList)

            add("%L(", result.function.asMemberName().toString())
            val size = result.function.parameters.size
            result.function.parameters.forEachIndexed { i, param ->
                if (i != 0) {
                    add(",")
                }
                add(provide(TypeKey(param.type), context) { context.findWithIndex(it, i, size) })
            }
            add(")")

            endControlFlow()
        }.build()
    }

    private fun provideLazy(result: Result.Lazy, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("lazy {")
            add(provide(result.key, context))
            endControlFlow()
        }.build()
    }

    private fun provideArg(result: Result.Arg): CodeBlock {
        return CodeBlock.of(result.argName)
    }

    private fun Context.findWithIndex(key: TypeKey, index: Int, size: Int): Result? {
        val indexFromEnd = size - index - 1
        args.asReversed().forEachIndexed { i, (type, name) ->
            if (i == indexFromEnd && type.isAssignableFrom(key.type)) {
                return Result.Arg(name)
            }
        }
        return find(key)
    }

    private fun Context.find(key: TypeKey): Result? {
        when (val result = collector.resolve(key)) {
            is TypeCreator.Constructor -> {
                return if (result.scopedComponent != null && skipScoped != result.constructor.type) {
                    Result.Scoped(name = result.accessor, astClass = result.scopedComponent)
                } else {
                    Result.Constructor(result.constructor)
                }
            }
            is TypeCreator.Method -> {
                return if (result.scopedComponent != null && skipScoped != result.method.returnType) {
                    Result.Scoped(name = result.accessor, astClass = result.scopedComponent)
                } else {
                    Result.Provides(name = result.accessor, method = result.method)
                }
            }
            is TypeCreator.Container -> {
                return Result.Container(creator = result.creator.toString(), methods = result.methods)
            }
        }
        if (key.type.name.matches(Regex("kotlin\\.Function[0-9]+.*"))) {
            val typeAliasName = key.type.typeAliasName
            if (typeAliasName != null) {
                // Check to see if we have a function matching the type alias
                val packageName = typeAliasName.substringBeforeLast('.')
                val simpleName = typeAliasName.substringAfterLast('.')
                val functions = findFunctions(packageName, simpleName)
                val injectedFunction = functions.find { it.hasAnnotation<Inject>() }
                if (injectedFunction != null) {
                    return Result.NamedFunction(
                        function = injectedFunction,
                        args = key.type.arguments.dropLast(1)
                    )
                }
            }
            val fKey = TypeKey(key.type.arguments.last())
            return Result.Function(
                key = fKey,
                args = key.type.arguments.dropLast(1)
            )
        }
        if (key.type.name == "kotlin.Lazy") {
            val lKey = TypeKey(key.type.arguments[0])
            return Result.Lazy(key = lKey)
        }
        return null
    }

    data class Context(
        val source: AstClass,
        val collector: TypeCollector,
        val scopeInterface: AstClass? = null,
        val args: List<Pair<AstType, String>> = emptyList(),
        val skipScoped: AstType? = null
    ) {
        fun withoutScoped(scoped: AstType) = copy(skipScoped = scoped)

        fun withArgs(args: List<Pair<AstType, String>>) = copy(args = args)
    }

    sealed class Result(val name: String?) {
        class Provides(name: String?, val method: AstMethod) : Result(name)
        class Scoped(name: String?, val astClass: AstClass) : Result(name)
        class Constructor(val constructor: AstConstructor) : Result(null)
        class Container(val creator: String, val methods: List<AstMethod>) : Result(null)
        class Function(val key: TypeKey, val args: List<AstType>) : Result(null)
        class NamedFunction(val function: AstFunction, val args: List<AstType>) : Result(null)

        class Arg(val argName: String) : Result(null)
        class Lazy(val key: TypeKey) : Result(null)
    }

}


fun AstElement.scopeType(): AstClass? = typeAnnotatedWith<Scope>()

private fun String.asScopedProp(): String = "_" + decapitalize()

fun AstElement.isComponent() = hasAnnotation<Component>()

fun AstMethod.isProvides(): Boolean = hasAnnotation<Provides>()

fun AstMethod.isProvider(): Boolean =
    !hasAnnotation<Provides>() && AstModifier.ABSTRACT in modifiers && when (this) {
        is AstFunction -> parameters.isEmpty()
        is AstProperty -> true
    } && receiverParameterType == null && returnType.isNotUnit()

fun AstClass.scopeClass(messenger: Messenger): AstClass? {
    var elementScopeClass: AstClass? = null
    visitInheritanceChain { parentClass ->
        val parentScope = parentClass.scopeType()
        if (parentScope != null) {
            if (elementScopeClass == null) {
                elementScopeClass = parentClass
            } else {
                messenger.error("Cannot apply scope: $parentScope", parentClass)
                messenger.error("as scope: ${elementScopeClass!!.scopeType()} is already applied", elementScopeClass!!)
            }
        }
    }
    return elementScopeClass
}
