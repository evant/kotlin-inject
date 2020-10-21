package me.tatarka.inject.compiler

class CycleDetector {

    private val _elements = mutableListOf<AstElement>()

    val elements: List<AstElement> get() = _elements

    fun check(element: AstElement): CycleResult {
        return if (element in _elements) {
            // TODO: check resolvable
            CycleResult.Cycle
        } else {
            _elements.add(element)
            CycleResult.None
        }
    }

    fun pop() {
        _elements.removeAt(_elements.lastIndex)
    }
}

enum class CycleResult {
    None, Resolvable, Cycle
}
