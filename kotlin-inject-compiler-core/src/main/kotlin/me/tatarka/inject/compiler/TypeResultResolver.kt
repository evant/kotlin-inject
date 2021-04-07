package me.tatarka.inject.compiler

import me.tatarka.inject.annotations.Inject

/**
 * Obtains a [TypeResult] from a given [Context].
 */
@Suppress("NAME_SHADOWING", "FunctionNaming")
class TypeResultResolver(private val options: Options) {

    private val types = mutableMapOf<TypeKey, TypeResult>()

    /**
     * Resolves the given type in this context. This will return a cached result if has already been resolved.
     */
    fun resolve(context: Context, key: TypeKey): TypeResultRef {
        return TypeResultRef(key, types[key] ?: context.findType(key).linkRefs()
            .also { types[key] = it })
    }

    private fun <T : TypeResult> T.linkRefs(): T = also {
        it.children.forEach { child -> child.ref.parents.add(it) }
    }

    /**
     * Resolves the given type with the given index & size. This is used for matching positional types in function args.
     */
    private fun resolveWithIndex(context: Context, key: TypeKey, index: Int, size: Int): TypeResultRef {
        val indexFromEnd = size - index - 1
        context.args.asReversed().getOrNull(indexFromEnd)?.let { (type, name) ->
            if (type.isAssignableFrom(key.type)) {
                return TypeResultRef(key, TypeResult.Arg(name))
            }
        }
        return resolve(context, key)
    }

    /**
     * Find the given type.
     */
    private fun Context.findType(key: TypeKey): TypeResult {
        val typeCreator = collector.resolve(key)
        if (typeCreator != null) {
            return typeCreator.toResult(this, key, skipScoped)
        }
        if (key.type.isFunction()) {
            val resolveType = key.type.resolvedType()
            if (key.type.isTypeAlias()) {
                // Check to see if we have a function matching the type alias
                val functions = provider.findFunctions(key.type.packageName, key.type.simpleName)
                val injectedFunction = functions.find { it.hasAnnotation<Inject>() }
                if (injectedFunction != null) {
                    return NamedFunction(
                        this,
                        function = injectedFunction,
                        args = resolveType.arguments.dropLast(1)
                    )
                }
            }
            val fKey = TypeKey(resolveType.arguments.last(), key.qualifier)
            return Function(
                this,
                element = key.type.toAstClass(),
                key = fKey,
                args = resolveType.arguments.dropLast(1)
            )
        }
        if (key.type.packageName == "kotlin" && key.type.simpleName == "Lazy") {
            val lKey = TypeKey(key.type.arguments[0], key.qualifier)
            return Lazy(this, key = lKey)
        }
        throw FailedToGenerateException(trace("Cannot find an @Inject constructor or provider for: $key"))
    }

    private fun TypeCreator.toResult(context: Context, key: TypeKey, skipScoped: AstType?): TypeResult {
        return when (this@toResult) {
            is TypeCreator.Constructor ->
                if (scopedComponent != null && skipScoped != constructor.type) {
                    Scoped(context, accessor, key)
                } else {
                    Constructor(context, constructor)
                }
            is TypeCreator.Method ->
                if (scopedComponent != null && skipScoped != method.returnType) {
                    Scoped(context, accessor, key)
                } else {
                    Provides(context, accessor, method)
                }
            is TypeCreator.Container -> Container(context, creator.toString(), args)
            is TypeCreator.Object -> TypeResult.Object(astClass.type)
        }
    }

    private fun Provides(
        context: Context,
        accessor: String?,
        method: AstMethod,
    ) = context.use(method) { context ->
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
                val size = it.parameters.size
                it.parameters.mapIndexed { i, param ->
                    val key = TypeKey(param.type, param.qualifier(options))
                    resolveWithIndex(context, key, i, size)
                }
            } ?: emptyList(),
        )
    }

    private fun Scoped(
        context: Context,
        accessor: String?,
        key: TypeKey,
    ) = TypeResult.Scoped(
        key = key.toString(),
        accessor = accessor,
        result = resolve(context.withoutScoped(key.type), key)
    )

    private fun Constructor(context: Context, constructor: AstConstructor) = context.use(constructor) { context ->
        val size = constructor.parameters.size
        TypeResult.Constructor(
            type = constructor.type,
            parameters = constructor.parameters.mapIndexed { i, param ->
                val key = TypeKey(param.type, param.qualifier(options))
                resolveWithIndex(context, key, i, size)
            }
        )
    }

    private fun Container(
        context: Context,
        creator: String,
        args: List<TypeCreator.Method>
    ) = TypeResult.Container(creator = creator,
        args = args.map { arg ->
            val key = TypeKey(arg.method.returnType, arg.method.qualifier(options))
            TypeResultRef(key, Provides(context, arg.accessor, arg.method))
        }
    )

    private fun Function(
        context: Context,
        element: AstElement,
        key: TypeKey,
        args: List<AstType>
    ) = context.use(element) { context ->
        val namedArgs = args.mapIndexed { i, arg -> arg to "arg$i" }
        TypeResult.Function(
            args = namedArgs.map { it.second },
            result = resolve(context.withArgs(namedArgs), key)
        )
    }

    private fun NamedFunction(
        context: Context,
        function: AstFunction,
        args: List<AstType>
    ) = context.use(function) { context ->
        val namedArgs = args.mapIndexed { i, arg -> arg to "arg$i" }
        val size = function.parameters.size
        TypeResult.NamedFunction(
            name = function.asMemberName().toString(),
            args = namedArgs.map { it.second },
            parameters = function.parameters.mapIndexed { i, param ->
                val key = TypeKey(param.type, param.qualifier(options))
                resolveWithIndex(context.withArgs(namedArgs), key, i, size)
            }
        )
    }

    private fun Lazy(context: Context, key: TypeKey) = TypeResult.Lazy(resolve(context, key))
}