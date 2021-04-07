package me.tatarka.inject.compiler

import java.util.Locale

@Suppress("NestedBlockDepth")
fun List<MethodEntry>.optimize(): List<MethodEntry> {
    val newEntries = mutableListOf<MethodEntry>()
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
            val topLevel = find { it.typeResult == ref }
            val newResult = if (topLevel != null) {
                TypeResult.Provides(
                    className = "",
                    methodName = topLevel.name,
                    accessor = null,
                    receiver = null,
                    isProperty = true,
                    parameters = emptyList(),
                )
            } else {
                TypeResult.Provides(
                    className = "",
                    methodName = "_${key.type.simpleName.decapitalize(Locale.US)}",
                    accessor = null,
                    receiver = null,
                    isProperty = true,
                    parameters = emptyList(),
                ).also { newVar ->
                    newEntries.add(
                        MethodEntry.privateGetter(newVar.methodName, key.type, TypeResultRef(key, currentResult))
                    )
                }
            }
            for (parent in parents) {
                for (child in parent.children) {
                    if (child.result == currentResult) {
                        child.result = newResult
                    }
                }
            }
            visited.add(currentResult)
        }
        ref.result.children.forEach { visit(it) }
    }

    for (entry in this) {
        visit(entry.typeResult)
    }

    return newEntries + this
}

private fun List<MethodEntry>.collectParents(): Map<TypeResult, Set<TypeResult>> {
    val parentMap = mutableMapOf<TypeResult, MutableSet<TypeResult>>()

    fun collectParents(ref: TypeResultRef) {
        val parent = ref.result
        parent.children.forEach { child ->
            parentMap.getOrPut(child.result) { mutableSetOf() }.add(parent)
            collectParents(child)
        }
    }
    for (entry in this) {
        collectParents(entry.typeResult)
    }

    return parentMap
}