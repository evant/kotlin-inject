package me.tatarka.inject.compiler

class CycleDetector {

    private val _elements = mutableListOf<Entry>()
    private var resolving: CycleResult.Resolvable? = null

    val elements: List<AstElement>
        get() = _elements.mapNotNull {
            when (it) {
                is Entry.Element -> it.value
                else -> null
            }
        }

    fun delayed() {
        _elements.add(Entry.Delayed)
    }

    fun resolve(key: TypeKey): Boolean {
        return if (resolving?.key == key) {
            resolving = null
            true
        } else {
            false
        }
    }

    fun check(key: TypeKey, element: AstElement): CycleResult {
        return if (_elements.any { it is Entry.Element && it.value == element }) {
            if (_elements.any { it is Entry.Delayed }) {
                CycleResult.Resolvable(key).also { resolving = it }
            } else {
                CycleResult.Cycle
            }
        } else {
            _elements.add(Entry.Element(element))
            CycleResult.None
        }
    }

    fun pop() {
        while (_elements.removeAt(_elements.lastIndex) is Entry.Delayed) {
            // ignore
        }
    }

    private sealed class Entry {
        class Element(val value: AstElement) : Entry()
        object Delayed : Entry()
    }
}

sealed class CycleResult {
    object None : CycleResult()
    object Cycle : CycleResult()
    class Resolvable(val key: TypeKey) : CycleResult()
}