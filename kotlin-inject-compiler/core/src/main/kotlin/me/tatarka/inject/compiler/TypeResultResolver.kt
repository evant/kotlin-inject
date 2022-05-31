package me.tatarka.inject.compiler

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
        val typeCreator = types.resolve(key)
        if (typeCreator != null) {
            types.checkScope(key, typeCreator.scopedComponent(), element, scopeComponent)
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
                element = element,
                args = resolveType.arguments.dropLast(1)
            )
        }
        if (key.type.packageName == "kotlin" && key.type.simpleName == "Lazy") {
            val lKey = TypeKey(key.type.arguments[0], key.qualifier)
            return Lazy(this, element = element, key = lKey)
        }
        return null
    }

    private fun TypeCreator.toResult(context: Context, key: TypeKey, skipScoped: AstType?): TypeResult {
        return when (this@toResult) {
            is TypeCreator.Constructor ->
                if (scopedComponent != null && skipScoped != constructor.type) {
                    Scoped(context, accessor, constructor, scopedComponent, key)
                } else {
                    Constructor(context, constructor, key)
                }
            is TypeCreator.Method ->
                if (scopedComponent != null && skipScoped != method.returnType) {
                    Scoped(context, accessor, method, scopedComponent, key)
                } else {
                    Provides(context, accessor, method, key)
                }
            is TypeCreator.Container -> Container(context, creator.toString(), args)
            is TypeCreator.Object -> TypeResult.Object(astClass.type)
        }
    }

    private fun TypeCreator.scopedComponent(): AstClass? {
        return when (this) {
            is TypeCreator.Constructor -> scopedComponent
            is TypeCreator.Method -> scopedComponent
            else -> null
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
        context: Context,
        creator: String,
        args: List<TypeCreator.Method>
    ) = TypeResult.Container(
        creator = creator,
        args = args.map { arg ->
            val key = TypeKey(arg.method.returnType, arg.method.qualifier(options))
            TypeResultRef(key, Provides(context, arg.accessor, arg.method, key))
        }
    )

    private fun Function(
        context: Context,
        key: TypeKey,
        element: AstElement,
        args: List<AstType>
    ): TypeResult? {
        cycleDetector.delayedConstruction()
        val namedArgs = args.mapIndexed { i, arg -> arg to "arg$i" }
        val result = resolveOrNull(context.withArgs(namedArgs), element, key) ?: return null
        return TypeResult.Function(args = namedArgs.map { it.second }, result = result)
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
            name = function.toMemberName(),
            args = namedArgs.map { it.second },
            parameters = resolveParams(context.withArgs(namedArgs), function, function.parameters),
        )
    }

    private fun Lazy(context: Context, element: AstElement, key: TypeKey): TypeResult? {
        cycleDetector.delayedConstruction()
        val result = resolveOrNull(context, element, key) ?: return null
        return maybeLateInit(key, TypeResult.Lazy(result))
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