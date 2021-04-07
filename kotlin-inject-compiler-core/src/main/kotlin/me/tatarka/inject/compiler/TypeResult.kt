package me.tatarka.inject.compiler

/**
 * [TypeResult] wrapper that allows updating it's value.
 */
class TypeResultRef(val key: TypeKey, var result: TypeResult)

/**
 * Represents a certain type that can be constructed.
 */
sealed class TypeResult {

    private object EmptyIterator : Iterator<TypeResultRef> {
        override fun hasNext(): Boolean = false

        override fun next(): TypeResultRef = throw NoSuchElementException()
    }

    open val children: Iterator<TypeResultRef> = EmptyIterator

    /**
     * A function or property that can provide the type.
     */
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

    /**
     * The type is scoped to key.
     */
    class Scoped(
        val key: String,
        val accessor: String?,
        val result: TypeResultRef,
    ) : TypeResult() {
        override val children
            get() = iterator { yield(result) }
    }

    /**
     * A constructor for the the type.
     */
    class Constructor(
        val type: AstType,
        val parameters: List<TypeResultRef>
    ) : TypeResult() {
        override val children
            get() = parameters.iterator()
    }

    /**
     * A container that holds the type (ex: Set or Map).
     */
    class Container(
        val creator: String,
        val args: List<TypeResultRef>
    ) : TypeResult() {
        override val children
            get() = args.iterator()
    }

    /**
     * A lambda function that returns the type.
     */
    class Function(
        val args: List<String>,
        val result: TypeResultRef,
    ) : TypeResult() {
        override val children
            get() = iterator { yield(result) }
    }

    /**
     * A named function that returns the type.
     */
    class NamedFunction(
        val name: String,
        val args: List<String>,
        val parameters: List<TypeResultRef>,
    ) : TypeResult() {
        override val children
            get() = parameters.iterator()
    }

    /**
     * An object type.
     */
    class Object(val type: AstType) : TypeResult()

    /**
     * An arg that represents the type. Used in provides & functions.
     */
    class Arg(val name: String) : TypeResult()

    /**
     * A lazy type.
     */
    class Lazy(val result: TypeResultRef) : TypeResult() {
        override val children
            get() = iterator { yield(result) }
    }
}