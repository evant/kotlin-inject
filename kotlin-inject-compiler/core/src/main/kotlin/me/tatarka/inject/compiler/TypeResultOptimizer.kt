package me.tatarka.inject.compiler

@Suppress("NestedBlockDepth", "LoopWithTooManyJumpStatements")
fun List<TypeResult.Provider>.optimize(context: Context): List<TypeResult.Provider> {
    val newResults = mutableListOf<TypeResult.Provider>()

    // If a node has multiple parents and has a least one child, insert a getter node above it.
    // This reduces duplicated code by replacing ex:
    // ```
    // override fun getBar(): Bar = Bar(Foo(Dep1(), Dep2()))
    // override fun getBaz(): Baz = Baz(Foo(Dep1(), Dep2()))
    // ```
    // with
    // ```
    // private val _foo: Foo
    //   get() = Foo(Dep1(), Dep2())
    // override fun getBar(): Bar = Bar(_foo)
    // override fun getBaz(): Baz = Baz(_foo)
    // ```
    val hashes = map { TypeResultRef(TypeKey(it.returnType), it) }.groupByRef()

    for (refs in hashes.values) {
        // only combine if multiple instances found
        if (refs.size < 2) continue
        val key = refs.first().key
        val result = refs.first().result
        // Don't bother combining leaf refs as that doesn't really save anything
        if (!result.children.hasNext()) continue
        var provider = find { it.result.key == key }
        val name = provider?.name ?: context.nameAllocator.newName(key.type.toVariableName())
        val newResult = TypeResult.Provides(
            className = context.className,
            methodName = name,
            isProperty = provider?.isProperty ?: true,
        )
        if (provider == null) {
            provider = TypeResult.Provider(
                name = name,
                returnType = key.type,
                isProperty = true,
                isPrivate = true,
                result = TypeResultRef(key, result)
            )
            newResults.add(provider)
        }
        for (ref in refs) {
            // skip providers to prevent an infinite loop
            if (ref !== provider.result) {
                ref.result = newResult
            }
        }
    }

    return newResults + this
}

private fun List<TypeResultRef>.groupByRef(): Map<TypeResult, List<TypeResultRef>> {
    val hashes = mutableMapOf<TypeResult, MutableList<TypeResultRef>>()

    fun groupByRef(result: TypeResultRef) {
        val existing = hashes.getOrPut(result.result) { mutableListOf() }
        if (existing.isEmpty()) {
            result.result.children.forEach { child ->
                groupByRef(child)
            }
        }
        existing.add(result)
    }

    for (entry in this) {
        groupByRef(entry)
    }

    return hashes
}