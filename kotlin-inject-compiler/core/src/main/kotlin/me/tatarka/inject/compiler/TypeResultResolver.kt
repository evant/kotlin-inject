package me.tatarka.inject.compiler

import me.tatarka.inject.compiler.TypeResult.Object
import me.tatarka.kotlin.ast.AstAnnotation
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstConstructor
import me.tatarka.kotlin.ast.AstElement
import me.tatarka.kotlin.ast.AstFunction
import me.tatarka.kotlin.ast.AstMember
import me.tatarka.kotlin.ast.AstParam
import me.tatarka.kotlin.ast.AstProperty
import me.tatarka.kotlin.ast.AstProvider
import me.tatarka.kotlin.ast.AstType

/**
 * Obtains a [TypeResult] from a given [Context].
 */
@Suppress("NAME_SHADOWING", "FunctionNaming", "FunctionName")
class TypeResultResolver(private val provider: AstProvider, private val options: Options) {

    private val cycleDetector = CycleDetector()
    private val resolvedProvides = mutableMapOf<ResolveProvidesKey, TypeResult.Provides>()
    private val resolveProviders = mutableListOf<TypeResult.Provider>()

    /**
     * Resolves all [TypeResult] for provider methods in the given class.
     */
    fun resolveAll(context: Context, astClass: AstClass): List<TypeResult.Provider> {
        val results = ArrayList<TypeResult.Provider>()
        for (member in context.types.providerMembers) {
            results.add(
                Provider(
                    context,
                    astClass,
                    member.member,
                    member.qualifier,
                )
            )
        }
        results.addAll(0, resolveProviders)
        return results
    }

    /**
     * Resolves the given type in this context. This will return a cached result if has already been resolved.
     * @throws FailedToGenerateException if cannot be found
     */
    private fun resolve(context: Context, element: AstElement, key: TypeKey): TypeResultRef {
        return resolveOrNull(context, element, key)
            ?: throw FailedToGenerateException(cannotFind(key))
    }

    /**
     * Resolves the given type in this context. This will return a cached result if has already been resolved. Returns
     * null if it cannot be found.
     */
    private fun resolveOrNull(context: Context, element: AstElement, key: TypeKey): TypeResultRef? {
        val providesKey = context.providesKey(key)

        if (context.skipProvider != key.type) {
            val resolved = resolvedProvides[providesKey]
            if (resolved != null) {
                return TypeResultRef(key, resolved)
            }
        }

        val name = context.nameAllocator.newName("${key.type.toVariableName()}__Provider")

        // lazily evaluate findType() as it may cycle and need a resolved provides ref
        val type = lazy(LazyThreadSafetyMode.NONE) {
            context.withoutProvider(key.type).findType(element, key)
        }

        val isSuspend = {
            val resolvedType = type.value
            resolvedType is TypeResult.Provides && resolvedType.isSuspend()
        }
        val isProperty = { context.args.isEmpty() && !isSuspend() }

        val provides = TypeResult.Provides(
            className = context.className,
            methodName = name,
            isProperty = isProperty,
            isSuspend = isSuspend,
            args = context.args.associate { (type, name) ->
                val key = TypeKey(type)
                name to TypeResultRef(key, TypeResult.Arg(name))
            },
        )
        resolvedProvides[providesKey] = provides

        val resolvedType = type.value
        if (resolvedType == null) {
            resolvedProvides.remove(providesKey)
            return null
        }

        val result = TypeResultRef(key, provides)

        resolveProviders.add(
            TypeResult.Provider(
                name = name,
                returnType = key.type,
                isProperty = isProperty(),
                isPrivate = true,
                isSuspend = isSuspend(),
                parameters = context.args,
                result = TypeResultRef(key, resolvedType)
            )
        )
        return result
    }

    private fun Context.providesKey(key: TypeKey): ResolveProvidesKey {
        return ResolveProvidesKey(key, args.map { (type, _) -> TypeKey(type) })
    }

    /**
     * Resolves the given set of params. It will match position types in function args. If a param cannot be resolved,
     * but it has as default, it will be skipped.
     */
    private fun resolveArgs(
        context: Context,
        element: AstElement,
        scope: AstAnnotation?,
        params: List<AstParam>,
    ): Map<String, TypeResultRef> {
        return if (params.any { it.isAssisted() }) {
            resolveArgsNew(context, element, scope, params)
        } else {
            resolveArgsLegacy(context, element, scope, params)
        }
    }

    // new behavior where we only consider args annotated with @Assisted
    @Suppress("NestedBlockDepth")
    private fun resolveArgsNew(
        context: Context,
        element: AstElement,
        scope: AstAnnotation?,
        params: List<AstParam>,
    ): Map<String, TypeResultRef> {
        if (scope != null) {
            throw FailedToGenerateException(
                "Cannot apply scope: $scope to type with @Assisted parameters: [${
                    params.filter { it.isAssisted() }.joinToString()
                }]"
            )
        }
        val paramsWithName = LinkedHashMap<String, TypeResultRef>(context.args.size)
        var assistedFailed = false
        val args = context.args.toMutableList()
        for (param in params) {
            val resolvedType = if (param.type.isTypeAlias()) param.type.resolvedType() else param.type
            val type = if (resolvedType.isPlatform()) resolvedType.makeNonNullable() else resolvedType

            val qualifier = qualifier(provider, options, param, type)
            val key = TypeKey(type, qualifier)
            if (param.isAssisted()) {
                val arg = args.removeFirstOrNull()
                if (arg != null) {
                    val (type, name) = arg
                    if (type.isAssignableFrom(key.type)) {
                        paramsWithName[param.name] = TypeResultRef(key, TypeResult.Arg(name))
                    } else {
                        assistedFailed = true
                    }
                } else if (!param.hasDefault) {
                    assistedFailed = true
                }
            } else {
                val result = resolveOrNull(context.withoutArgs(), element, key)
                if (result != null) {
                    paramsWithName[param.name] = result
                } else if (!param.hasDefault) {
                    throw FailedToGenerateException(cannotFind(key))
                }
            }
        }

        if (args.isNotEmpty()) {
            assistedFailed = true
        }

        if (assistedFailed) {
            throw FailedToGenerateException(
                """
                    Mismatched @Assisted parameters.
                    Expected: [${
                    params.filter { it.isAssisted() }.joinToString()
                }]
                    But got:  [${
                    context.args.joinToString { (type, _) -> type.toString() }
                }]
                """.trimIndent(),
                element
            )
        }
        return paramsWithName
    }

    // old behavior where the last args are used, error.
    private fun resolveArgsLegacy(
        context: Context,
        element: AstElement,
        scope: AstAnnotation?,
        params: List<AstParam>,
    ): Map<String, TypeResultRef> {
        val size = params.size
        val paramsWithName = LinkedHashMap<String, TypeResultRef>(size)
        val args = context.args.asReversed()
        val resolvedImplicitly = mutableListOf<AstParam>()
        for ((i, param) in params.withIndex()) {
            val indexFromEnd = size - i - 1
            val type = if (param.type.isPlatform()) {
                param.type.makeNonNullable()
            } else {
                param.type
            }
            val qualifier = qualifier(provider, options, param, type)
            val key = TypeKey(type, qualifier)
            val arg = args.getOrNull(indexFromEnd)
            if (arg != null) {
                val (type, name) = arg
                if (type.isAssignableFrom(key.type)) {
                    resolvedImplicitly.add(param)
                    paramsWithName[param.name] = TypeResultRef(key, TypeResult.Arg(name))
                    continue
                }
            }
            val result = resolveOrNull(context.withoutArgs(), element, key)
            if (result != null) {
                paramsWithName[param.name] = result
            } else if (!param.hasDefault) {
                throw FailedToGenerateException(cannotFind(key))
            }
        }

        if (resolvedImplicitly.isNotEmpty()) {
            provider.error(
                """
                Implicit assisted parameters is no longer supported.
                Annotate the following with @Assisted: [${resolvedImplicitly.joinToString()}]
                """.trimIndent(),
                element
            )
            if (scope != null) {
                throw FailedToGenerateException(
                    "Cannot apply scope: $scope to type with @Assisted parameters: [${
                        resolvedImplicitly.joinToString()
                    }]"
                )
            }
        }

        return paramsWithName
    }

    /**
     * Find the given type.
     */
    private fun Context.findType(element: AstElement, key: TypeKey): TypeResult? {
        if (key.type.isError) {
            throw FailedToGenerateException(trace("Unresolved reference: $key"))
        }

        if (skipProvider != key.type) {
            val resolvedProvider = resolvedProvides[providesKey(key)]
            if (resolvedProvider != null) {
                return resolvedProvider
            }
        }

        val providerResult = types.providerType(key)
        if (providerResult != null) {
            val (member, types) = providerResult
            return Provides(
                context = withTypes(types),
                accessor = member.accessor,
                method = member.method,
                scope = null,
                key = key,
            )
        }

        val result = types.type(key)
        if (result != null) {
            val (creator, types) = result
            return withTypes(types).method(key, creator)
        }

        if (key.type.isSet()) {
            return set(key)
        }

        if (key.type.isMap()) {
            return map(key)
        }

        if (key.type.isFunctionOrTypeAliasOfFunction()) {
            return functionType(element, key)
        }

        if (key.type.isLazy()) {
            val lKey = TypeKey(key.type.arguments[0], key.qualifier)
            return Lazy(key = lKey) {
                resolveOrNull(this, element = element, key = lKey) ?: return null
            }
        }

        val astClass = key.type.toAstClass()

        if (astClass.isObject && astClass.isInject()) {
            return Object(astClass.type)
        }

        val injectCtor = astClass.findInjectConstructors(provider.messenger, options)
        if (injectCtor != null) {
            return constructor(key, injectCtor, astClass)
        }

        if (astClass.isAssistedFactory()) {
            return assistedFactory(astClass, key)
        }

        return null
    }

    private fun Context.method(key: TypeKey, creator: Member): TypeResult {
        return if (creator.scopedComponent != null && skipScoped != key) {
            Scoped(
                context = this,
                accessor = creator.accessor,
                element = creator.method,
                scopedComponent = creator.scopedComponent,
                key = key,
            )
        } else {
            Provides(
                context = this,
                accessor = creator.accessor,
                method = creator.method,
                scope = creator.scope,
                key = key,
            )
        }
    }

    private fun Context.set(key: TypeKey): TypeResult? {
        val innerType = key.type.arguments[0]

        val containerKey = ContainerKey.SetKey(innerType, key.qualifier)
        val args = types.containerArgs(containerKey)
        if (args.isNotEmpty()) {
            return Container(
                creator = containerKey.creator,
                args = args,
                mapArg = { key, arg, types ->
                    Provides(withTypes(types), arg.accessor, arg.method, arg.scope, key)
                }
            )
        }

        if (innerType.isFunction()) {
            val containerKey = ContainerKey.SetKey(innerType.arguments.last(), key.qualifier)
            val args = types.containerArgs(containerKey)
            if (args.isEmpty()) return null
            return Container(
                creator = containerKey.creator,
                args = args,
                mapArg = { key, arg, types ->
                    Function(withTypes(types), parameters = innerType.arguments.dropLast(1)) { context ->
                        TypeResultRef(key, Provides(context, arg.accessor, arg.method, arg.scope, key))
                    }
                }
            )
        }

        if (innerType.isLazy()) {
            val containerKey = ContainerKey.SetKey(innerType.arguments[0], key.qualifier)
            val args = types.containerArgs(containerKey)
            if (args.isEmpty()) return null
            return Container(
                creator = containerKey.creator,
                args = args,
                mapArg = { key, arg, types ->
                    Lazy(key) {
                        TypeResultRef(key, Provides(withTypes(types), arg.accessor, arg.method, arg.scope, key))
                    }
                }
            )
        }
        return null
    }

    private fun Context.map(key: TypeKey): TypeResult? {
        val type = key.type.resolvedType()
        val containerKey = ContainerKey.MapKey(type.arguments[0], type.arguments[1], key.qualifier)
        val args = types.containerArgs(containerKey)
        if (args.isEmpty()) return null
        return Container(
            creator = containerKey.creator,
            args = args,
            mapArg = { key, arg, types ->
                Provides(withTypes(types), arg.accessor, arg.method, arg.scope, key)
            }
        )
    }

    private fun Context.assistedFactory(astClass: AstClass, key: TypeKey): TypeResult? {
        val factoryFunction = findAssistedFactoryFunction(astClass)

        val namedArgs = factoryFunction.parameters.map { param -> param.type to param.name }
        val injectFunction = findAssistedFactoryInjectFunction(
            context = this,
            key = key,
            astClass = astClass,
            factoryFunction = factoryFunction
        )

        if (injectFunction == null) {
            return TypeResult.AssistedFactory(
                factoryType = key.type,
                function = factoryFunction,
                parameters = namedArgs,
                result = resolveOrNull(
                    context = withArgs(namedArgs),
                    element = factoryFunction,
                    key = TypeKey(factoryFunction.returnType)
                ) ?: return null,
            )
        } else {
            return TypeResult.AssistedFunctionFactory(
                factoryType = key.type,
                function = factoryFunction,
                parameters = namedArgs,
                injectFunction = injectFunction,
                injectFunctionParameters = resolveArgs(
                    context = withArgs(namedArgs),
                    element = injectFunction,
                    scope = null,
                    params = injectFunction.parameters
                ),
            )
        }
    }

    private fun Context.functionType(element: AstElement, key: TypeKey): TypeResult? {
        val resolveType = key.type.resolvedType()
        val args = resolveType.arguments.dropLast(1)
        if (key.type.isTypeAlias()) {
            val resolvedTypeAlias = key.type.fullyResolvedType()

            // Check to see if we have a function matching the type alias
            val functions = provider.findFunctions(resolvedTypeAlias.packageName, resolvedTypeAlias.simpleName)
            val injectedFunction = functions.find { it.isInject() }
            if (injectedFunction != null) {
                return NamedFunction(
                    context = this,
                    function = injectedFunction,
                    key = key,
                    args = args,
                )
            }
        }
        val fKey = TypeKey(resolveType.arguments.last(), key.qualifier)
        return Function(this, parameters = args) { context ->
            resolveOrNull(context, element = element, key = fKey) ?: return null
        }
    }

    private fun Context.constructor(key: TypeKey, injectCtor: AstConstructor, astClass: AstClass): TypeResult {
        val scope = astClass.scope(options)
        val scopedResult = if (scope != null) types.scopedAccessor(scope) else null
        if (scope != null && scopedResult == null) {
            val checkedComponents = types.iterator().asSequence().map { result ->
                buildString {
                    val scope = result.astClass.scope(options)
                    if (scope != null) {
                        append("$scope ")
                    }
                    append(result.astClass)
                }
            }
            throw FailedToGenerateException(
                """Cannot find component with scope: $scope to inject $astClass
                    |checked: [${checkedComponents.joinToString(", ")}]
                """.trimMargin(),
                astClass,
            )
        }
        return if (scopedResult != null && skipScoped != key) {
            val (scopedComponent, types) = scopedResult
            Scoped(
                context = withTypes(types),
                accessor = scopedComponent.accessor,
                element = injectCtor,
                scopedComponent = scopedComponent.type,
                key = key,
            )
        } else {
            Constructor(
                context = this,
                constructor = injectCtor,
                outerClassType = astClass.outerClass?.type,
                scope = scope,
                key = key,
            )
        }
    }

    private fun Provider(
        context: Context,
        astClass: AstClass,
        method: AstMember,
        qualifier: AstAnnotation?,
    ): TypeResult.Provider {
        val returnType = method.returnTypeFor(astClass)
        val key = TypeKey(returnType, qualifier)
        val result = withCycleDetection(context, key, method) {
            resolve(context.withoutProvider(returnType), method, key).result
        }
        return TypeResult.Provider(
            name = method.name,
            returnType = returnType,
            isProperty = method is AstProperty,
            isPrivate = false,
            isOverride = true,
            isSuspend = method is AstFunction && method.isSuspend,
            result = TypeResultRef(key, result)
        )
    }

    private fun Provides(
        context: Context,
        accessor: Accessor,
        method: AstMember,
        scope: AstAnnotation?,
        key: TypeKey,
    ): TypeResult {
        if (scope != null && method is AstFunction && method.isSuspend) {
            throw FailedToGenerateException(
                "@Provides scoped with $scope cannot be suspend, consider returning Deferred<T> instead."
            )
        }
        return withCycleDetection(context, key, method) {
            TypeResult.Provides(
                className = context.className,
                methodName = method.name,
                accessor = accessor,
                receiver = method.receiverParameterType?.let {
                    val qualifier = qualifier(provider, options, null, it)
                    val key = TypeKey(it, qualifier)
                    resolve(context, method, key)
                },
                isProperty = { method is AstProperty },
                isSuspend = { method is AstFunction && method.isSuspend },
                args = (method as? AstFunction)?.let {
                    resolveArgs(context, method, scope, it.parameters)
                } ?: emptyMap(),
            )
        }
    }

    private fun Scoped(
        context: Context,
        accessor: Accessor,
        element: AstElement,
        scopedComponent: AstClass,
        key: TypeKey,
    ) = TypeResult.Scoped(
        key = key,
        accessor = accessor,
        result = resolve(context.withoutScoped(key, scopedComponent), element, key)
    )

    private fun Constructor(
        context: Context,
        constructor: AstConstructor,
        outerClassType: AstType?,
        scope: AstAnnotation?,
        key: TypeKey,
    ) = withCycleDetection(context, key, constructor) {
        TypeResult.Constructor(
            type = constructor.type,
            scope = scope,
            outerClass = outerClassType?.let { resolve(context, constructor, TypeKey(it)) },
            args = resolveArgs(context, constructor, scope, constructor.parameters),
            supportsNamedArguments = constructor.supportsNamedArguments
        )
    }

    private fun Container(
        creator: String,
        args: List<Pair<Member, TypeCollector.Result>>,
        mapArg: (TypeKey, Member, TypeCollector.Result) -> TypeResult,
    ): TypeResult {
        return TypeResult.Container(
            creator = creator,
            args = args.associate { (arg, types) ->
                val returnType = arg.method.returnType
                val qualifier = qualifier(provider, options, arg.method, returnType)
                val key = TypeKey(returnType, qualifier)
                "" to TypeResultRef(key, mapArg(key, arg, types))
            }
        )
    }

    private inline fun Function(
        context: Context,
        parameters: List<AstType>,
        result: (context: Context) -> TypeResultRef,
    ): TypeResult {
        // The current cycle resolution does not handle args because it re-uses the same instance.
        if (parameters.isEmpty()) {
            cycleDetector.delayedConstruction()
        }
        val context = context.copyNameAllocator()
        val namedParams = parameters.mapIndexed { i, arg ->
            arg to context.nameAllocator.newName("arg$i")
        }
        return TypeResult.Function(
            parameters = namedParams.map { it.second },
            result = result(context.withArgs(namedParams))
        )
    }

    private fun NamedFunction(
        context: Context,
        function: AstFunction,
        key: TypeKey,
        args: List<AstType>,
    ) = withCycleDetection(context, key, function) {
        // Drop receiver from args
        val namedArgs = if (function.receiverParameterType != null) {
            args.drop(1)
        } else {
            args
        }.mapIndexed { i, arg ->
            arg to context.nameAllocator.newName("arg$i")
        }
        TypeResult.NamedFunction(
            name = function.toMemberName(),
            parameters = namedArgs.map { it.second },
            args = resolveArgs(
                context = context.withArgs(namedArgs),
                element = function,
                scope = null,
                params = function.parameters
            ),
        )
    }

    private inline fun Lazy(key: TypeKey, result: () -> TypeResultRef): TypeResult {
        cycleDetector.delayedConstruction()
        return TypeResult.Lazy(result())
    }

    private fun withCycleDetection(
        context: Context,
        key: TypeKey,
        source: AstElement,
        f: () -> TypeResult,
    ): TypeResult {
        val result = cycleDetector.check(key, source) { cycleResult ->
            when (cycleResult) {
                is CycleResult.None -> f()
                is CycleResult.Cycle -> throw FailedToGenerateException(trace("Cycle detected"))
                is CycleResult.Resolvable -> resolvedProvides.getValue(context.providesKey(cycleResult.key))
            }
        }
        return result
    }

    /**
     * Produce a trace with the given message prefix. This will show all the lines with
     * elements that were traversed for this context.
     */
    private fun trace(message: String): String = "$message\n" + cycleDetector.trace(provider)

    private fun cannotFind(key: TypeKey): String = trace("Cannot find an @Inject constructor or provider for: $key")

    private val TypeResult.isCacheable: Boolean
        // don't cache local vars as they may not be in scope when requesting the type from a different location
        get() = this !is TypeResult.LocalVar && children.all {
            it.result is TypeResult.LateInit || it.result.isCacheable
        }

    private inline fun <T> Iterator<T>.all(predicate: (T) -> Boolean): Boolean {
        for (item in this) {
            if (!predicate(item)) {
                return false
            }
        }
        return true
    }

    private fun AstType.isLazy(): Boolean = packageName == "kotlin" && simpleName == "Lazy"

    private fun AstParam.isAssisted(): Boolean = hasAnnotation(ASSISTED.packageName, ASSISTED.simpleName)

    private data class ResolveProvidesKey(
        val key: TypeKey,
        val assistedParams: List<TypeKey> = emptyList(),
    )
}
