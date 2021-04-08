package me.tatarka.inject.compiler

/**
 * A context to find types in. Holds enough info to figure out how to obtain the type.
 */
data class Context(
    val provider: AstProvider,
    val className: String,
    val collector: TypeCollector,
    val scopeInterface: AstClass? = null,
    val args: List<Pair<AstType, String>> = emptyList(),
    val skipScoped: AstType? = null,
    val skipProvider: AstType? = null,
) {
    fun withoutScoped(scoped: AstType) = copy(skipScoped = scoped)

    fun withoutProvider(provider: AstType) = copy(skipProvider = provider)

    fun withArgs(args: List<Pair<AstType, String>>) = copy(args = args)
}