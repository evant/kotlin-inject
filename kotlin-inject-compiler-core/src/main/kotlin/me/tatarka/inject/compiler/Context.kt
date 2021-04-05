package me.tatarka.inject.compiler

/**
 * A context to find types in. Holds enough info to figure out how to obtain the type.
 */
data class Context(
    val provider: AstProvider,
    val className: String,
    val source: AstElement,
    val collector: TypeCollector,
    val cycleDetector: CycleDetector,
    val scopeInterface: AstClass? = null,
    val args: List<Pair<AstType, String>> = emptyList(),
    val skipScoped: AstType? = null,
    val skipProvider: AstType? = null,
) {
    fun withoutScoped(scoped: AstType) = copy(skipScoped = scoped)

    fun withoutProvider(provider: AstType) = copy(skipProvider = provider)

    fun withSource(source: AstElement) = copy(source = source)

    fun withArgs(args: List<Pair<AstType, String>>) = copy(args = args)

    fun <T> use(source: AstElement, f: (context: Context) -> T): T {
        return when (cycleDetector.check(source)) {
            CycleResult.None -> f(withSource(source)).also { cycleDetector.pop() }
            CycleResult.Cycle -> throw FailedToGenerateException(trace("Cycle detected"))
            CycleResult.Resolvable -> TODO()
        }
    }

    /**
     * Produce a trace with the given message prefix. This will show all the lines with
     * elements that were traversed for this context.
     */
    fun trace(message: String): String = "$message\n" +
            cycleDetector.elements.reversed()
                .joinToString(separator = "\n") { with(provider) { it.toTrace() } }
}