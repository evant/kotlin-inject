package me.tatarka.inject.compiler

@Suppress("NestedBlockDepth")
fun List<TypeResult.Provider>.optimize(context: Context): List<TypeResult.Provider> {
    val newResults = mutableListOf<TypeResult.Provider>()
    val visited = mutableSetOf<TypeResult>()
    val parentMap = collectParents()

    fun visit(ref: TypeResultRef) {
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
        val key = ref.key
        val currentResult = ref.result
        val parents = parentMap[currentResult] ?: emptySet()
        if (currentResult !in visited && parents.size > 1 && currentResult.children.hasNext()) {
            var provider = find { it.result == ref }
            val name = provider?.name ?: "_${key.type.toVariableName()}"
            val newResult = TypeResult.Provides(
                className = context.className,
                methodName = name,
                isProperty = true,
            )
            if (provider == null) {
                provider = TypeResult.Provider(
                    name = name,
                    returnType = key.type,
                    isProperty = true,
                    isPrivate = true,
                    result = TypeResultRef(key, currentResult)
                ).also { newResults.add(it) }
            }

            parents.updateRefs(
                self = provider,
                oldValue = currentResult,
                newValue = newResult
            )

            visited.add(currentResult)
        }
        ref.result.children.forEach { visit(it) }
    }

    for (entry in this) {
        visit(entry.result)
    }

    return newResults + this
}

private fun List<TypeResult>.collectParents(): Map<TypeResult, Set<TypeResult>> {
    val parentMap = mutableMapOf<TypeResult, MutableSet<TypeResult>>()

    fun collectParents(result: TypeResult) {
        result.children.forEach { child ->
            parentMap.getOrPut(child.result) { mutableSetOf() }.add(result)
            collectParents(child.result)
        }
    }
    for (entry in this) {
        collectParents(entry)
    }

    return parentMap
}

private fun Set<TypeResult>.updateRefs(self: TypeResult, oldValue: TypeResult, newValue: TypeResult) {
    for (result in this) {
        // skip self to prevent an infinite loop
        if (result == self) continue
        for (child in result.children) {
            if (child.result == oldValue) {
                child.result = newValue
            }
        }
    }
}