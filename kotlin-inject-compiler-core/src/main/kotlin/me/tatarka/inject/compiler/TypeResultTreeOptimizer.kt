package me.tatarka.inject.compiler

import java.util.Locale

@Suppress("NestedBlockDepth")
fun List<MethodEntry>.optimize(): List<MethodEntry> {
    val newEntries = mutableListOf<MethodEntry>()
    val visited = mutableSetOf<TypeResult>()

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
        val oldRef = ref.ref
        if (oldRef !in visited && oldRef.parents.size > 1 && oldRef.children.hasNext()) {
            val topLevel = find { it.typeResult == ref }
            val newVar = if (topLevel != null) {
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
                        MethodEntry.privateGetter(newVar.methodName, key.type, TypeResultRef(key, oldRef))
                    )
                }
            }
            for (parent in oldRef.parents) {
                for (child in parent.children) {
                    if (child.ref == oldRef) {
                        child.ref = newVar
                    }
                }
            }
            oldRef.parents.apply {
                clear()
                add(newVar)
            }
            visited.add(oldRef)
        }
        ref.ref.children.forEach { visit(it) }
    }

    for (entry in this) {
        visit(entry.typeResult)
    }

    return newEntries + this
}