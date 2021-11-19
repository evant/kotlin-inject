package me.tatarka.inject.compiler

/**
 * Obtains a [TypeResult] from a given [Context].
 */
@Suppress("NAME_SHADOWING", "FunctionNaming")
class TypeResultResolver(private val provider: AstProvider, private val options: Options) {

    private val cycleDetector = CycleDetector()
    private val types = mutableMapOf<TypeKey, TypeResult>()

    /**
     * Resolves all [TypeResult] for provider methods in the given class.
     */
    fun resolveAll(context: Context, astClass: AstClass): List<TypeResult.Provider> {
        return context.types.providerMethods.map { method ->
            Provider(context, astClass, method)
        }
    }

    /**
     * Resolves the given type in this context. This will return a cached result if has already been resolved.
     * @throws FailedToGenerateException if cannot be found
     */
    private fun resolve(context: Context, key: TypeKey): TypeResultRef {
        return resolveOrNull(context, key)
            ?: throw FailedToGenerateException(cannotFind(key))
    }

    /**
     * Resolves the given type in this context. This will return a cached result if has already been resolved. Returns
     * null if it cannot be found.
     */
    private fun resolveOrNull(context: Context, key: TypeKey): TypeResultRef? {
        val type = types[key] ?: context.findType(key)?.also {
            if (it.isCacheable) {
                types[key] = it
            }
        }
        return type?.let { TypeResultRef(key, it) }
    }

    /**
     * Resolves the given set of params. It will match position types in function args. If a param cannot be resolved,
     * but it has as default, it will be skipped.
     */
    private fun resolveParams(context: Context, params: List<AstParam>): Map<String, TypeResultRef> {
        val size = params.size
        val args = context.args.asReversed()
        val paramsWithName = LinkedHashMap<String, TypeResultRef>(size)
        params.forEachIndexed { i, param ->
            val indexFromEnd = size - i - 1
            val key = TypeKey(param.type, param.qualifier(options))
            val arg = args.getOrNull(indexFromEnd)
            if (arg != null) {
                val (type, name) = arg
                if (type.isAssignableFrom(key.type)) {
                  paramsWithName[param.name] = TypeResultRef(key, TypeResult.Arg(name))
                  return@forEachIndexed
                }
            }
            val result = resolveOrNull(context, key)
            if (result != null) {
                paramsWithName[param.name] = result
                return@forEachIndexed
            }
            if (!param.hasDefault) {
                throw FailedToGenerateException(cannotFind(key))
            }
        }
        return paramsWithName
    }

    /**
     * Find the given type.
     */
    private fun Context.findType(key: TypeKey): TypeResult? {
        if (key.type.isError) {
            throw FailedToGenerateException(trace("Unresolved reference: $key"))
        }
        val typeCreator = types.resolve(key)
        if (typeCreator != null) {
            return typeCreator.toResult(this, key, skipScoped)
        }
        if (key.type.isFunction()) {
            val resolveType = key.type.resolvedType()
            if (key.type.isTypeAlias()) {
                // Check to see if we have a function matching the type alias
                val functions = provider.findFunctions(key.type.packageName, key.type.simpleName)
                val injectedFunction = functions.find { it.isInject() }
                if (injectedFunction != null) {
                    return NamedFunction(
                        this,
                        function = injectedFunction,
                        key = key,
                        args = resolveType.arguments.dropLast(1)
                    )
                }
            }
            val fKey = TypeKey(resolveType.arguments.last(), key.qualifier)
            return Function(
                this,
                key = fKey,
                args = resolveType.arguments.dropLast(1)
            )
        }
        if (key.type.packageName == "kotlin" && key.type.simpleName == "Lazy") {
            val lKey = TypeKey(key.type.arguments[0], key.qualifier)
            return Lazy(this, key = lKey)
        }
        return null
    }

    private fun TypeCreator.toResult(context: Context, key: TypeKey, skipScoped: AstType?): TypeResult {
        return when (this@toResult) {
            is TypeCreator.Constructor ->
                if (scopedComponent != null && skipScoped != constructor.type) {
                    Scoped(context, accessor, key)
                } else {
                    Constructor(context, constructor, key)
                }
            is TypeCreator.Method ->
                if (scopedComponent != null && skipScoped != method.returnType) {
                    Scoped(context, accessor, key)
                } else {
                    Provides(context, accessor, method, key)
                }
            is TypeCreator.Container -> Container(context, creator.toString(), args)
            is TypeCreator.Object -> TypeResult.Object(astClass.type)
        }
    }

    private fun Provider(
        context: Context,
        astClass: AstClass,
        method: AstMethod,
    ): TypeResult.Provider {
        val returnType = method.returnTypeFor(astClass)
        val key = TypeKey(returnType, method.qualifier(options))
        val result = withCycleDetection(key, method) {
            resolve(context.withoutProvider(returnType), key).result
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
        method: AstMethod,
        key: TypeKey,
    ) = withCycleDetection(key, method) {
        TypeResult.Provides(
            className = context.className,
            methodName = method.name,
            accessor = accessor,
            receiver = method.receiverParameterType?.let {
                val key = TypeKey(it, method.qualifier(options))
                resolve(context, key)
            },
            isProperty = method is AstProperty,
            parameters = (method as? AstFunction)?.let {
                resolveParams(context, it.parameters)
            } ?: emptyMap(),
        )
    }

    private fun Scoped(
        context: Context,
        accessor: Accessor,
        key: TypeKey,
    ) = TypeResult.Scoped(
        key = key,
        accessor = accessor,
        result = resolve(context.withoutScoped(key.type), key)
    )

    private fun Constructor(context: Context, constructor: AstConstructor, key: TypeKey) =
        withCycleDetection(key, constructor) {
            TypeResult.Constructor(
                type = constructor.type,
                parameters = resolveParams(context, constructor.parameters),
                supportsNamedArguments = constructor.supportsNamedArguments
            )
        }

    private fun Container(
        context: Context,
        creator: String,
        args: List<TypeCreator.Method>
    ) = TypeResult.Container(creator = creator,
        args = args.map { arg ->
            val key = TypeKey(arg.method.returnType, arg.method.qualifier(options))
            TypeResultRef(key, Provides(context, arg.accessor, arg.method, key))
        }
    )

    private fun Function(
        context: Context,
        key: TypeKey,
        args: List<AstType>
    ): TypeResult {
        cycleDetector.delayedConstruction()
        val namedArgs = args.mapIndexed { i, arg -> arg to "arg$i" }
        return TypeResult.Function(
            args = namedArgs.map { it.second },
            result = resolve(context.withArgs(namedArgs), key)
        )
    }

    private fun NamedFunction(
        context: Context,
        function: AstFunction,
        key: TypeKey,
        args: List<AstType>
    ) = withCycleDetection(key, function) {
        // Drop receiver from args
        val namedArgs = if (function.receiverParameterType != null) {
            args.drop(1)
        } else {
            args
        }.mapIndexed { i, arg -> arg to "arg$i" }
        TypeResult.NamedFunction(
            name = function.asMemberName(),
            args = namedArgs.map { it.second },
            parameters = resolveParams(context.withArgs(namedArgs), function.parameters),
        )
    }

    private fun Lazy(context: Context, key: TypeKey): TypeResult {
        cycleDetector.delayedConstruction()
        return maybeLateInit(key, TypeResult.Lazy(resolve(context, key)))
    }

    private fun LateInit(
        name: String,
        key: TypeKey,
        typeResult: TypeResult
    ): TypeResult.LateInit {
        return TypeResult.LateInit(
            name = name,
            result = TypeResultRef(key, typeResult)
        )
    }

    private fun withCycleDetection(
        key: TypeKey,
        source: AstElement,
        f: () -> TypeResult
    ): TypeResult {
        val result = cycleDetector.check(key, source) { cycleResult ->
            when (cycleResult) {
                is CycleResult.None -> f()
                is CycleResult.Cycle -> throw FailedToGenerateException(trace("Cycle detected"))
                is CycleResult.Resolvable -> TypeResult.LocalVar(cycleResult.name)
            }
        }
        return maybeLateInit(key, result)
    }

    private fun maybeLateInit(key: TypeKey, result: TypeResult): TypeResult {
        // TODO: better way to determine this?
        val validResultType =
            result !is TypeResult.LocalVar && result !is TypeResult.Lazy && result !is TypeResult.Function
        if (!validResultType) return result
        val name = cycleDetector.hitResolvable(key) ?: return result
        return LateInit(name, key, result)
    }

    /**
     * Produce a trace with the given message prefix. This will show all the lines with
     * elements that were traversed for this context.
     */
    private fun trace(message: String): String = "$message\n" + cycleDetector.trace(provider)

    private fun cannotFind(key: TypeKey): String = trace("Cannot find an @Inject constructor or provider for: $key")

    private val TypeResult.isCacheable: Boolean
        // don't cache local vars as the may not be in scope when requesting the type from a different location
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
}