package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import me.tatarka.inject.annotations.*
import java.util.*
import kotlin.reflect.KClass

class InjectGenerator(provider: AstProvider, private val options: Options) :
    AstProvider by provider {

    fun generate(astClass: AstClass, scopedInjects: Collection<AstClass> = emptyList()): FileSpec {
        if (AstModifier.ABSTRACT !in astClass.modifiers) {
            throw FailedToGenerateException("@Component class: $astClass must be abstract", astClass)
        } else if (AstModifier.PRIVATE in astClass.modifiers) {
            throw FailedToGenerateException("@Component class: $astClass must not be private", astClass)
        }

        val constructor = astClass.primaryConstructor

        val injectComponent = generateInjectComponent(astClass, constructor, scopedInjects)
        val createFunction = generateCreate(astClass, constructor, injectComponent)

        return FileSpec.builder(astClass.packageName, "Inject${astClass.name}")
            .addType(injectComponent)
            .addFunction(createFunction)
            .build()
    }

    private fun generateInjectComponent(
        astClass: AstClass,
        constructor: AstConstructor?,
        scopedInjects: Collection<AstClass>
    ): TypeSpec {
        val context = collectTypes(astClass, scopedInjects)

        return TypeSpec.classBuilder("Inject${astClass.name}")
            .addOriginatingElement(astClass)
            .superclass(astClass.asClassName())
            .apply {
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
                    for ((type, _) in context.scoped) {
                        val codeBlock = CodeBlock.builder()
                        codeBlock.add("lazy { ")
                        codeBlock.add(provide(TypeKey(type), context.withoutScoped()))
                        codeBlock.add(" }")

                        addProperty(
                            PropertySpec.builder(
                                type.asElement().simpleName.asScopedProp(),
                                type.asTypeName()
                            ).delegate(codeBlock.build())
                                .build()
                        )
                    }

                    astClass.visitInheritanceChain { parentClass ->
                        for (method in parentClass.methods) {
                            if (method.isProvider()) {
                                val codeBlock = CodeBlock.builder()
                                codeBlock.add("return ")
                                val returnType = method.returnTypeFor(astClass)

                                codeBlock.add(provide(TypeKey(returnType), context))

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
        name: String? = null,
        typesWithScope: MutableMap<AstType, AstClass> = mutableMapOf()
    ): Context {

        val providesMap = mutableMapOf<TypeKey, AstMethod>()
        val providesContainer = mutableMapOf<TypeKey, Pair<String, MutableList<AstMethod>>>()

        val scoped = mutableMapOf<AstType, AstClass>()

        val elementScope = astClass.scopeType()

        val itr = typesWithScope.iterator()
        while (itr.hasNext()) {
            val (type, typeScope) = itr.next()
            if (elementScope == typeScope) {
                scoped[type] = astClass
                itr.remove()
            }
        }

        for (injectClass in scopedInjects) {
            scoped[injectClass.type] = injectClass
        }

        fun addScope(type: AstType, scope: AstClass?) {
            if (scope != null) {
                if (scope == elementScope) {
                    scoped[type] = astClass
                } else {
                    typesWithScope[type] = scope
                }
            }
        }

        astClass.visitInheritanceChain { parentClass ->
            for (method in parentClass.methods) {
                if (method.isProvides()) {
                    if (method.modifiers.contains(AstModifier.PRIVATE)) {
                        error("@Provides method must not be private", method)
                        continue
                    }
                    if (method.modifiers.contains(AstModifier.ABSTRACT)) {
                        error("@Provides method must not be abstract", method)
                        continue
                    }
                    if (method.returnType.isUnit()) {
                        error("@Provides method must return a value", method)
                        continue
                    }

                    if (method.hasAnnotation<IntoMap>()) {
                        // Pair<A, B> -> Map<A, B>
                        val typeArgs = method.returnTypeFor(astClass).arguments
                        val mapType = declaredTypeOf(Map::class, typeArgs[0], typeArgs[1])

                        providesContainer.computeIfAbsent(TypeKey(mapType)) {
                            "mapOf" to mutableListOf()
                        }.second.add(method)
                    } else if (method.hasAnnotation<IntoSet>()) {
                        // A -> Set<A>
                        val setType = declaredTypeOf(Set::class, method.returnTypeFor(astClass))

                        providesContainer.computeIfAbsent(TypeKey(setType)) {
                            "setOf" to mutableListOf()
                        }.second.add(method)
                    } else {
                        val returnType = method.returnTypeFor(astClass)
                        val key = TypeKey(returnType)
                        val oldValue = providesMap[key]
                        if (oldValue == null) {
                            providesMap[key] = method
                            addScope(returnType, method.scopeType())
                        } else {
                            error("Cannot provide: $key", method)
                            error("as it is already provided", oldValue)
                        }
                    }
                }
                if (method.isProvider()) {
                    val returnType = method.returnType.asElement()
                    addScope(method.returnType, returnType.scopeType())
                }
            }
        }

        val parents = mutableListOf<Context>()

        val constructor = astClass.primaryConstructor
        if (constructor != null) {
            for (parameter in constructor.parameters) {
                if (parameter.isComponent()) {
                    val elemAstClass = parameter.type.toAstClass()
                    parents.add(
                        collectTypes(
                            elemAstClass,
                            scopedInjects,
                            name = if (name != null) "$name.${parameter.name}" else parameter.name,
                            typesWithScope = typesWithScope
                        )
                    )
                }
            }
        }
        return Context(
            source = astClass,
            name = name,
            parents = parents,
            provides = providesMap,
            providesContainer = providesContainer,
            scoped = scoped
        )
    }

    private fun provide(
        key: TypeKey,
        context: Context,
        find: (TypeKey) -> Result? = { context.find(it) }
    ): CodeBlock {
        val result = find(key)
            ?: throw FailedToGenerateException("Cannot find an @Inject constructor or provider for: $key", context.source)
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
        if (providesResult.name != null) {
            codeBlock.add("%L.", providesResult.name)
        }
        val method = providesResult.method

        val receiverParamType = method.receiverParameterType

        when (method) {
            is AstProperty -> {
                if (receiverParamType != null) {
                    codeBlock.add(provide(TypeKey(receiverParamType), context))
                    codeBlock.add(".")
                }
                codeBlock.add("%N", method.name)
            }
            is AstFunction -> {
                if (receiverParamType != null) {
                    codeBlock.add(provide(TypeKey(receiverParamType), context))
                    codeBlock.add(".")
                }
                codeBlock.add("%N(", method.name)
                val size = method.parameters.size
                method.parameters.forEachIndexed { i, param ->
                    if (i != 0) {
                        codeBlock.add(",")
                    }
                    codeBlock.add(provide(TypeKey(param.type), context) { context.findWithIndex(it, i, size) })
                }
                codeBlock.add(")")
            }
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
            codeBlock.add(provide(TypeKey(param.type), context) { context.findWithIndex(it, i, size) })
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
                add(provideProvides(
                    Result.Provides(
                        null,
                        method
                    ), context))
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

    private fun AstParam.isComponent() = hasAnnotation<Component>()

    private fun AstMethod.isProvides(): Boolean = hasAnnotation<Provides>()

    private fun AstMethod.isProvider(): Boolean =
        modifiers.contains(AstModifier.ABSTRACT) && when (this) {
            is AstFunction -> parameters.isEmpty()
            is AstProperty -> true
        } && receiverParameterType == null && returnType.isNotUnit()

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
        provides[key]?.let { result ->
            return Result.Provides(name, result)
        }
        providesContainer[key]?.let { (creator, elements) ->
            return Result.Container(creator, elements)
        }
        scoped[key.type]?.let { result ->
            return Result.Scoped(name, result)
        }
        if (key.type.name.matches(Regex("kotlin\\.Function[0-9]+.*"))) {
            val typeAliasName = key.type.typeAliasName
            if (typeAliasName != null) {
                // Check to see if we have a function matching the type alias
                val functions = findFunctions(typeAliasName)
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
        for (p in parents) {
            val parentResult = p.find(key)
            if (parentResult != null) {
                return parentResult
            }
        }
        val astClass = key.type.toAstClass()
        if (astClass.hasAnnotation<Inject>()) {
            val scope = astClass.scopeType()
            val sourceScopeType = source?.scopeType()
            if (scope != null && scope != sourceScopeType) {
                error("Cannot find module with scope: @$scope to inject $astClass", astClass)
                return null
            }
            return Result.Constructor(astClass.primaryConstructor!!)
        }
        return null
    }

    data class Context(
        val source: AstClass? = null,
        val name: String? = null,
        val parents: List<Context>,
        val provides: Map<TypeKey, AstMethod>,
        val providesContainer: Map<TypeKey, Pair<String, List<AstMethod>>>,
        val scoped: Map<AstType, AstClass> = emptyMap(),
        val args: List<Pair<AstType, String>> = emptyList()
    ) {
        fun withoutScoped() = copy(scoped = emptyMap())

        fun withArgs(args: List<Pair<AstType, String>>) = copy(args = args)
    }

    sealed class Result(val name: String?) {
        class Provides(name: String?, val method: AstMethod) : Result(name)
        class Scoped(name: String?, val astClass: AstClass) : Result(name)
        class Constructor(val constructor: AstConstructor) : Result(null)
        class Container(val creator: String, val methods: List<AstMethod>) : Result(null)
        class Function(val key: TypeKey, val args: List<AstType>) : Result(null)
        class NamedFunction(val function: AstFunction, val args: List<AstType>): Result(null)

        class Arg(val argName: String) : Result(null)
        class Lazy(val key: TypeKey) : Result(null)
    }

    inner class TypeKey(val type: AstType) {
        val qualifier = type.typeAliasName

        override fun equals(other: Any?): Boolean {
            if (other !is TypeKey) return false
            return qualifier == other.qualifier && type == other.type
        }

        override fun hashCode(): Int {
            return Objects.hash(type, qualifier)
        }

        override fun toString(): String {
            return qualifier ?: type.toString()
        }
    }
}

private fun AstElement.scopeType(): AstClass? = typeAnnotatedWith<Scope>()

private fun String.asScopedProp(): String = "_" + decapitalize()
