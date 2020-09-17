package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope
import kotlin.reflect.KClass

class InjectGenerator(provider: AstProvider, private val options: Options) :
    AstProvider by provider {

    fun generate(astClass: AstClass): FileSpec {
        if (AstModifier.ABSTRACT !in astClass.modifiers) {
            throw FailedToGenerateException("@Component class: $astClass must be abstract", astClass)
        } else if (AstModifier.PRIVATE in astClass.modifiers) {
            throw FailedToGenerateException("@Component class: $astClass must not be private", astClass)
        }

        val constructor = astClass.primaryConstructor

        val injectComponent = generateInjectComponent(astClass, constructor)
        val createFunction = generateCreate(astClass, constructor, injectComponent)

        return FileSpec.builder(astClass.packageName, "Inject${astClass.name}")
            .addType(injectComponent)
            .addFunction(createFunction)
            .build()
    }

    private fun generateInjectComponent(
        astClass: AstClass,
        constructor: AstConstructor?
    ): TypeSpec {
        val context = collectTypes(astClass, isComponent = true)
        val scope = astClass.scopeClass(messenger, options)

        return TypeSpec.classBuilder("Inject${astClass.name}")
            .addOriginatingElement(astClass)
            .superclass(astClass.asClassName())
            .apply {
                if (scope != null) {
                    addSuperinterface(ClassName("me.tatarka.inject.internal", "ScopedComponent"))
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
                    if (scope != null) {
                        val mapType = ClassName("me.tatarka.inject.internal", "LazyMap")
                        addProperty(
                            PropertySpec.builder("_scoped", mapType)
                                .addModifiers(KModifier.OVERRIDE)
                                .initializer("%T()", mapType)
                                .build()
                        )
                    }

                    astClass.visitInheritanceChain { parentClass ->
                        for (method in parentClass.methods) {
                            if (method.isProvider()) {
                                val returnType = method.returnTypeFor(astClass)

                                context.withoutProvider(returnType).use(method) { context ->
                                    val codeBlock = CodeBlock.builder()
                                    codeBlock.add("return ")

                                    codeBlock.add(
                                        provide(
                                            key = TypeKey(returnType),
                                            context = context
                                        )
                                    )

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
                    }
                } catch (e: FailedToGenerateException) {
                    error(e.message.orEmpty(), e.element)
                    // Create a stub component to prevent extra compile errors, the original one will still be reported.
                }
            }
            .build()
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
        isComponent: Boolean
    ): Context {
        val typeCollector = TypeCollector(
            provider = this,
            options = options,
            astClass = astClass,
            isComponent = isComponent
        )
        val cycleDetector = CycleDetector()
        val elementScopeClass = astClass.scopeClass(messenger, options)
        val scopeFromParent = elementScopeClass != astClass
        return Context(
            provider = this,
            source = astClass,
            collector = typeCollector,
            cycleDetector = cycleDetector,
            scopeInterface = if (scopeFromParent) elementScopeClass else null
        )
    }

    private fun provide(
        key: TypeKey,
        context: Context,
        find: (Context, TypeKey) -> Result? = { context, key -> context.find(key) }
    ): CodeBlock {
        val result = find(context, key)
            ?: throw FailedToGenerateException(
                context.trace("Cannot find an @Inject constructor or provider for: $key")
            )
        return when (result) {
            is Result.Provides -> provideProvides(result, context)
            is Result.Scoped -> provideScoped(key, result, context)
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
        val method = providesResult.method
        return context.use(method) { context ->
            val codeBlock = CodeBlock.builder()

            val receiverParamType = method.receiverParameterType
            val changeScope = providesResult.name != null && receiverParamType != null
            val changeScopeName = providesResult.name?.let {
                if (context.parentScopeName != null) {
                    it.removePrefix("${context.parentScopeName}.")
                } else {
                    it
                }
            }

            if (providesResult.name != null) {
                if (changeScope) {
                    codeBlock.add("with(%L)", changeScopeName)
                    codeBlock.beginControlFlow(" {")
                } else {
                    codeBlock.add("%L.", changeScopeName)
                }
            }

            val context = if (changeScope) {
                context.withParentScopeName(providesResult.name)
            } else {
                context
            }

            when (method) {
                is AstProperty -> {
                    if (receiverParamType != null) {
                        codeBlock.add(provide(key = TypeKey(receiverParamType), context = context))
                        codeBlock.add(".")
                    }
                    codeBlock.add("%N", method.name)
                }
                is AstFunction -> {
                    if (receiverParamType != null) {
                        codeBlock.add(provide(key = TypeKey(receiverParamType), context = context))
                        codeBlock.add(".")
                    }
                    codeBlock.add("%N(", method.name)
                    val size = method.parameters.size
                    method.parameters.forEachIndexed { i, param ->
                        if (i != 0) {
                            codeBlock.add(",")
                        }
                        codeBlock.add(
                            provide(
                                key = TypeKey(param.type),
                                context = context
                            ) { context, key -> context.findWithIndex(key, i, size) })
                    }
                    codeBlock.add(")")
                }
            }

            if (changeScope) {
                codeBlock.endControlFlow()
            }

            codeBlock.build()
        }
    }

    private fun provideConstructor(
        constructor: AstConstructor,
        context: Context
    ): CodeBlock = context.use(constructor) { context ->
        val codeBlock = CodeBlock.builder()
        codeBlock.add("%T(", constructor.type.asTypeName())
        val size = constructor.parameters.size
        constructor.parameters.forEachIndexed { i, param ->
            if (i != 0) {
                codeBlock.add(",")
            }
            codeBlock.add(provide(key = TypeKey(param.type), context = context) { context, key ->
                context.findWithIndex(key, i, size)
            })
        }
        codeBlock.add(")")
        codeBlock.build()
    }

    private fun provideScoped(key: TypeKey, result: Result.Scoped, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            if (result.name != null) {
                add(
                    "(%L as %T).",
                    result.name,
                    ClassName("me.tatarka.inject.internal", "ScopedComponent")
                )
            }
            add("_scoped.get(%S)", key.type).beginControlFlow("{")
            add(provide(key, context.withoutScoped(key.type)))
            endControlFlow()
        }.build()
    }

    private fun provideContainer(containerResult: Result.Container, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            add("${containerResult.creator}(")
            containerResult.args.forEachIndexed { index, method ->
                if (index != 0) {
                    add(", ")
                }
                add(
                    provideProvides(method, context)
                )
            }
            add(")")
        }.build()
    }

    private fun provideFunction(result: Result.Function, context: Context): CodeBlock =
        context.use(result.element) { context ->
            CodeBlock.builder().apply {
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

    private fun provideNamedFunction(result: Result.NamedFunction, context: Context): CodeBlock =
        context.use(result.function) { context ->
            CodeBlock.builder().apply {
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

                add("%L(", result.function.asMemberName().toString())
                val size = result.function.parameters.size
                result.function.parameters.forEachIndexed { i, param ->
                    if (i != 0) {
                        add(",")
                    }
                    add(provide(TypeKey(param.type), context.withArgs(argList)) { context, key ->
                        context.findWithIndex(key, i, size)
                    })
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
        args.asReversed().getOrNull(indexFromEnd)?.let { (type, name) ->
            if (type.isAssignableFrom(key.type)) {
                return Result.Arg(name)
            }
        }
        return find(key)
    }

    private fun Context.find(key: TypeKey): Result? {
        val typeCreator = collector.resolve(key, skipSelf = key.type == skipProvider)
        if (typeCreator != null) {
            return typeCreator.toResult(skipScoped)
        }
        if (key.type.isFunction()) {
            val resolveType = key.type.resolvedType()
            if (key.type.isTypeAlis()) {
                // Check to see if we have a function matching the type alias
                val functions = findFunctions(key.type.packageName, key.type.simpleName)
                val injectedFunction = functions.find { it.hasAnnotation<Inject>() }
                if (injectedFunction != null) {
                    return Result.NamedFunction(
                        function = injectedFunction,
                        args = resolveType.arguments.dropLast(1)
                    )
                }
            }
            val fKey = TypeKey(resolveType.arguments.last())
            return Result.Function(
                element = key.type.toAstClass(),
                key = fKey,
                args = resolveType.arguments.dropLast(1)
            )
        }
        if (key.type.packageName == "kotlin" && key.type.simpleName == "Lazy") {
            val lKey = TypeKey(key.type.arguments[0])
            return Result.Lazy(key = lKey)
        }
        return null
    }

    private fun TypeCreator.toResult(skipScoped: AstType?): Result {
        when (this) {
            is TypeCreator.Constructor -> {
                return if (scopedComponent != null && skipScoped != constructor.type) {
                    Result.Scoped(name = accessor, astClass = scopedComponent)
                } else {
                    Result.Constructor(constructor)
                }
            }
            is TypeCreator.Method -> {
                return if (scopedComponent != null && skipScoped != method.returnType) {
                    Result.Scoped(name = accessor, astClass = scopedComponent)
                } else {
                    Result.Provides(name = accessor, method = method)
                }
            }
            is TypeCreator.Container -> {
                return Result.Container(
                    creator = creator.toString(),
                    args = args.map { it.toResult(skipScoped) as Result.Provides })
            }
        }
    }

    data class Context(
        val provider: AstProvider,
        val source: AstElement,
        val collector: TypeCollector,
        val cycleDetector: CycleDetector,
        val scopeInterface: AstClass? = null,
        val args: List<Pair<AstType, String>> = emptyList(),
        val skipScoped: AstType? = null,
        val skipProvider: AstType? = null,
        val parentScopeName: String? = null
    ) {
        fun withoutScoped(scoped: AstType) = copy(skipScoped = scoped)

        fun withoutProvider(provider: AstType) = copy(skipProvider = provider)

        fun withSource(source: AstElement) = copy(source = source)

        fun withParentScopeName(name: String?): Context {
            if (name == null) return this
            return copy(parentScopeName = if (parentScopeName == null) name else "$parentScopeName.$name")
        }

        fun withArgs(args: List<Pair<AstType, String>>) = copy(args = args)

        fun <T> use(source: AstElement, f: (context: Context) -> T): T {
            return when (cycleDetector.check(source)) {
                CycleResult.None -> {
                    f(withSource(source)).also {
                        cycleDetector.pop()
                    }
                }
                CycleResult.Cycle -> {
                    throw FailedToGenerateException(trace("Cycle detected"))
                }
                CycleResult.Resolvable -> TODO()
            }
        }

        fun trace(message: String): String = "$message\n" +
                cycleDetector.elements.reversed()
                    .joinToString(separator = "\n") { with(provider) { it.toTrace() } }
    }

    sealed class Result(val name: String?) {
        class Provides(name: String?, val method: AstMethod) : Result(name)
        class Scoped(name: String?, val astClass: AstClass) : Result(name)
        class Constructor(val constructor: AstConstructor) : Result(null)
        class Container(val creator: String, val args: List<Provides>) : Result(null)
        class Function(val element: AstElement, val key: TypeKey, val args: List<AstType>) : Result(null)
        class NamedFunction(val function: AstFunction, val args: List<AstType>) : Result(null)

        class Arg(val argName: String) : Result(null)
        class Lazy(val key: TypeKey) : Result(null)
    }

}


fun AstElement.scopeType(options: Options): AstClass? {
    if (options.enableJavaxAnnotations) {
        val type = typeAnnotatedWith("javax.inject.Scope")
        if (type != null) {
            return type
        }
    }
    return typeAnnotatedWith<Scope>()
}

private fun String.asScopedProp(): String = "_" + decapitalize()

fun AstElement.isComponent() = hasAnnotation<Component>()

fun AstMethod.isProvides(): Boolean = hasAnnotation<Provides>()

fun AstClass.isInject(options: Options): Boolean {
    if (options.enableJavaxAnnotations) {
        if (primaryConstructor?.hasAnnotation("javax.inject.Inject") == true) {
            return true
        }
    }
    return hasAnnotation<Inject>()
}

fun AstMethod.isProvider(): Boolean =
    !hasAnnotation<Provides>() && AstModifier.ABSTRACT in modifiers && when (this) {
        is AstFunction -> parameters.isEmpty()
        is AstProperty -> true
    } && receiverParameterType == null && returnType.isNotUnit()

fun AstClass.scopeClass(messenger: Messenger, options: Options): AstClass? {
    var elementScopeClass: AstClass? = null
    visitInheritanceChain { parentClass ->
        val parentScope = parentClass.scopeType(options)
        if (parentScope != null) {
            if (elementScopeClass == null) {
                elementScopeClass = parentClass
            } else {
                messenger.error("Cannot apply scope: $parentScope", parentClass)
                messenger.error(
                    "as scope: ${elementScopeClass!!.scopeType(options)} is already applied",
                    elementScopeClass!!
                )
            }
        }
    }
    return elementScopeClass
}
