package me.tatarka.inject.compiler

/**
 * Builds of a stack of elements visited to see if we hit a cycle. Normally a cycle will cause a compile error. However
 * the cycle can be 'broken' by delaying construction.
 */
class CycleDetector {

    private val entries = mutableListOf<Entry>()
    private val resolving = mutableListOf<Resolving>()

    /**
     * Denote that construction is being delayed. A cycle that crosses a delayed element can be resolved.
     */
    fun delayedConstruction() {
        entries.add(Entry.Delayed)
    }

    /**
     * Returns the variable name if [CycleResult.Resolvable] was hit for the given type key lower in the tree. This
     * means you should create the variable that was referenced.
     */
    fun hitResolvable(key: TypeKey): String? {
        return if (resolving.lastOrNull()?.key == key) {
            resolving.removeLast().name
        } else {
            null
        }
    }

    /**
     * Checks that the given element with the given type will produce a cycle. The result is provided in the given block
     * so that then state can be rest after recursing.
     *
     * @see CycleResult
     */
    fun <T> check(key: TypeKey, element: AstElement, block: (CycleResult) -> T): T {
        val cycleResult = if (entries.any { it is Entry.Element && it.value == element }) {
            if (entries.any { it is Entry.Delayed }) {
                val name = key.type.toVariableName()
                resolving.add(Resolving(key, name))
                CycleResult.Resolvable(name)
            } else {
                CycleResult.Cycle
            }
        } else {
            entries.add(Entry.Element(element))
            CycleResult.None
        }
        val result = block(cycleResult)
        // Pop back up, removing any Delayed entries as well.
        if (cycleResult == CycleResult.None) {
            while (entries.removeLastOrNull() is Entry.Delayed) {
                // ignore
            }
        }
        return result
    }

    /**
     * Produce a trace of visited elements.
     */
    fun trace(provider: AstProvider): String = entries.mapNotNull {
        // filter only elements with a source.
        when (it) {
            is Entry.Element -> it.value
            else -> null
        }
    }.reversed().joinToString(separator = "\n") { with(provider) { it.toTrace() } }

    private sealed class Entry {
        class Element(val value: AstElement) : Entry()
        object Delayed : Entry()
    }

    private class Resolving(val key: TypeKey, val name: String)
}

sealed class CycleResult {
    /**
     * There was no cycle, you may proceed normally.
     */
    object None : CycleResult()

    /**
     * There was a cycle, you should error out.
     */
    object Cycle : CycleResult()

    /**
     * There was a cycle but it was across a delayed construction so it can be resolved. Reference the variable here
     * with the given name and call [CycleDetector.hitResolvable] higher up the tree to create it.
     */
    class Resolvable(val name: String) : CycleResult()
}