package me.tatarka.inject.compiler

import me.tatarka.inject.compiler.TypeResult.Object
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
    private val nameAllocator = ArgNameAllocator()
    private val types = mutableMapOf<TypeKey, TypeResult>()

    /**
     * Resolves all [TypeResult] for provider methods in the given class.
     */
    fun resolveAll(context: Context, astClass: AstClass): List<TypeResult.Provider> {
        return context.types.providerMethods.map { method ->
            // reset the name allocator between methods so that arg names are not unique across
            // all functions of a component
            nameAllocator.reset()
            Provider(context, astClass, method)
        }
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
        val type = types[key] ?: context.findType(element, key)?.also {
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
    private fun resolveParams(
        context: Context,
        element: AstElement,
        params: List<AstParam>,
    ): Map<String, TypeResultRef> {
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
            val result = resolveOrNull(context, element, key)
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
    private fun Context.findType(element: AstElement, key: TypeKey): TypeResult? {
        if (key.type.isError) {
            throw FailedToGenerateException(trace("Unresolved reference: $key"))
        }

        val providerResult = types.providerType(key)
        if (providerResult != null) {
            val (method, types) = providerResult
            return Provides(
                context = withTypes(types),
                accessor = method.accessor,
                method = method.method,
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

        if (key.type.isFunction()) {
            return functionType(element, key)
        }

        if (key.type.isLazy()) {
            val lKey = TypeKey(key.type.arguments[0], key.qualifier)
            return Lazy(key = lKey) {
                resolveOrNull(this, element = element, key = lKey) ?: return null
            }
        }

        val astClass = key.type.toAstClass()
        val injectCtor = astClass.findInjectConstructors(provider.messenger, options)
        if (injectCtor != null) {
            return constructor(key, injectCtor, astClass)
        }

        if (astClass.isInject() && astClass.isObject) {
            return Object(astClass.type)
        }

        return null
    }

    private fun Context.method(key: TypeKey, creator: Method): TypeResult {
        return if (creator.scopedComponent != null && skipScoped != creator.method.returnType) {
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
                key = key,
            )
        }
    }

    private fun Context.set(key: TypeKey): TypeResult? {
        val innerType = key.type.arguments[0].resolvedType()
        if (innerType.isFunction()) {
            val containerKey = ContainerKey.SetKey(innerType.arguments.last(), key.qualifier)
            val args = types.containerArgs(containerKey)
            if (args.isEmpty()) return null
            return Container(
                creator = containerKey.creator,
                args = args,
                mapArg = { key, arg, types ->
                    Function(withTypes(types), args = innerType.arguments.dropLast(1)) { context ->
                        TypeResultRef(key, Provides(context, arg.accessor, arg.method, key))
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
                        TypeResultRef(key, Provides(withTypes(types), arg.accessor, arg.method, key))
                    }
                }
            )
        }

        val containerKey = ContainerKey.SetKey(innerType, key.qualifier)
        val args = types.containerArgs(containerKey)
        if (args.isEmpty()) return null
        return Container(
            creator = containerKey.creator,
            args = args,
            mapArg = { key, arg, types ->
                Provides(withTypes(types), arg.accessor, arg.method, key)
            }
        )
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
                Provides(withTypes(types), arg.accessor, arg.method, key)
            }
        )
    }

    private fun Context.functionType(element: AstElement, key: TypeKey): TypeResult? {
        val resolveType = key.type.resolvedType()
        if (key.type.isTypeAlias()) {
            // Check to see if we have a function matching the type alias
            val functions = provider.findFunctions(key.type.packageName, key.type.simpleName)
            val injectedFunction = functions.find { it.isInject() }
            if (injectedFunction != null) {
                return NamedFunction(
                    context = this,
                    function = injectedFunction,
                    key = key,
                    args = resolveType.arguments.dropLast(1),
                )
            }
        }
        val fKey = TypeKey(resolveType.arguments.last(), key.qualifier)
        return Function(this, args = resolveType.arguments.dropLast(1)) { context ->
            resolveOrNull(context, element = element, key = fKey) ?: return null
        }
    }

    private fun Context.constructor(key: TypeKey, injectCtor: AstConstructor, astClass: AstClass): TypeResult? {
        val scope = astClass.scopeType(options)
        val scopedResult = if (scope != null) types.scopedAccessor(scope) else null
        if (scope != null && scopedResult == null) {
            provider.error("Cannot find component with scope: @$scope to inject $astClass", astClass)
            return null
        }
        return if (scopedResult != null && skipScoped != injectCtor.type) {
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
                key = key,
            )
        }
    }

    private fun Provider(
        context: Context,
        astClass: AstClass,
        method: AstMember,
    ): TypeResult.Provider {
        val returnType = method.returnTypeFor(astClass)
        val key = TypeKey(returnType, method.qualifier(options))
        val result = withCycleDetection(key, method) {
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
        key: TypeKey,
    ) = withCycleDetection(key, method) {
        TypeResult.Provides(
            className = context.className,
            methodName = method.name,
            accessor = accessor,
            receiver = method.receiverParameterType?.let {
                val key = TypeKey(it, method.qualifier(options))
                resolve(context, method, key)
            },
            isProperty = method is AstProperty,
            parameters = (method as? AstFunction)?.let {
                resolveParams(context, method, it.parameters)
            } ?: emptyMap(),
        )
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
        result = resolve(context.withoutScoped(key.type, scopedComponent), element, key)
    )

    private fun Constructor(context: Context, constructor: AstConstructor, key: TypeKey) =
        withCycleDetection(key, constructor) {
            TypeResult.Constructor(
                type = constructor.type,
                parameters = resolveParams(context, constructor, constructor.parameters),
                supportsNamedArguments = constructor.supportsNamedArguments
            )
        }

    private fun Container(
        creator: String,
        args: List<Pair<Method, TypeCollector.Result>>,
        mapArg: (TypeKey, Method, TypeCollector.Result) -> TypeResult,
    ): TypeResult {
        return TypeResult.Container(
            creator = creator,
            args = args.map { (arg, types) ->
                val key = TypeKey(arg.method.returnType, arg.method.qualifier(options))
                TypeResultRef(key, mapArg(key, arg, types))
            }
        )
    }

    private inline fun Function(
        context: Context,
        args: List<AstType>,
        result: (context: Context) -> TypeResultRef,
    ): TypeResult {
        cycleDetector.delayedConstruction()
        val namedArgs = args.mapIndexed { i, arg ->
            arg to nameAllocator.newName(i)
        }
        return TypeResult.Function(args = namedArgs.map { it.second }, result = result(context.withArgs(namedArgs)))
    }

    private fun NamedFunction(
        context: Context,
        function: AstFunction,
        key: TypeKey,
        args: List<AstType>,
    ) = withCycleDetection(key, function) {
        // Drop receiver from args
        val namedArgs = if (function.receiverParameterType != null) {
            args.drop(1)
        } else {
            args
        }.mapIndexed { i, arg ->
            arg to nameAllocator.newName(i)
        }
        TypeResult.NamedFunction(
            name = function.toMemberName(),
            args = namedArgs.map { it.second },
            parameters = resolveParams(context.withArgs(namedArgs), function, function.parameters),
        )
    }

    private inline fun Lazy(key: TypeKey, result: () -> TypeResultRef): TypeResult {
        cycleDetector.delayedConstruction()
        return maybeLateInit(key, TypeResult.Lazy(result()))
    }

    private fun LateInit(
        name: String,
        key: TypeKey,
        typeResult: TypeResult,
    ): TypeResult.LateInit {
        return TypeResult.LateInit(
            name = name,
            result = TypeResultRef(key, typeResult)
        )
    }

    private fun withCycleDetection(
        key: TypeKey,
        source: AstElement,
        f: () -> TypeResult,
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
}