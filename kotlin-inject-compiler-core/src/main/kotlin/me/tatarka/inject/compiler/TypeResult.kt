package me.tatarka.inject.compiler

class TypeResultRef(val key: TypeKey, var ref: TypeResult)

/**
 * Represents a certain type that can be constructed.
 */
sealed class TypeResult {

    private object EmptyIterator : Iterator<TypeResultRef> {
        override fun hasNext(): Boolean = false

        override fun next(): TypeResultRef = throw NoSuchElementException()
    }

    val parents: MutableList<TypeResult> = mutableListOf()
    open val children: Iterator<TypeResultRef> = EmptyIterator

    class Provides(
        val className: String,
        val methodName: String,
        val accessor: String?,
        val receiver: TypeResultRef?,
        val isProperty: Boolean,
        val parameters: List<TypeResultRef>,
    ) : TypeResult() {
        override val children
            get() = iterator {
                receiver?.let { yield(it) }
                yieldAll(parameters)
            }
    }

    class Scoped(
        val key: String,
        val accessor: String?,
        val result: TypeResultRef,
    ) : TypeResult() {
        override val children
            get() = iterator { yield(result) }
    }

    class Constructor(
        val type: AstType,
        val parameters: List<TypeResultRef>
    ) : TypeResult() {
        override val children
            get() = parameters.iterator()
    }

    class Container(
        val creator: String,
        val args: List<TypeResultRef>
    ) : TypeResult() {
        override val children
            get() = args.iterator()
    }

    class Function(
        val args: List<String>,
        val result: TypeResultRef,
    ) : TypeResult() {
        override val children
            get() = iterator { yield(result) }
    }

    class NamedFunction(
        val name: String,
        val args: List<String>,
        val parameters: List<TypeResultRef>,
    ) : TypeResult() {
        override val children
            get() = parameters.iterator()
    }

    class Object(val type: AstType) : TypeResult()

    class Arg(val name: String) : TypeResult()

    class Lazy(val result: TypeResultRef) : TypeResult() {
        override val children
            get() = iterator { yield(result) }
    }
}