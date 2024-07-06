package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.MemberName
import me.tatarka.kotlin.ast.AstAnnotation
import me.tatarka.kotlin.ast.AstFunction
import me.tatarka.kotlin.ast.AstType

/**
 * [TypeResult] wrapper that allows updating it's value.
 */
class TypeResultRef(val key: TypeKey, var result: TypeResult)

/**
 * Represents a certain type that can be constructed.
 */
sealed class TypeResult {

    open val children: Iterator<TypeResultRef> = EmptyIterator

    /**
     * A function or property declaration that provides a type.
     */
    class Provider(
        val name: String,
        val returnType: AstType,
        val isProperty: Boolean = false,
        val isPrivate: Boolean = false,
        val isOverride: Boolean = false,
        val isSuspend: Boolean = false,
        val result: TypeResultRef,
    ) : TypeResult() {
        override val children: Iterator<TypeResultRef>
            get() = iterator { yield(result) }
    }

    /**
     * Calls a function or property that can provide the type.
     */
    class Provides(
        val className: String,
        val methodName: String,
        val accessor: Accessor = Accessor.Empty,
        val receiver: TypeResultRef? = null,
        val isProperty: Boolean = false,
        val parameters: Map<String, TypeResultRef> = emptyMap(),
    ) : TypeResult() {
        override val children
            get() = iterator {
                receiver?.let { yield(it) }
                yieldAll(parameters.values)
            }
    }

    /**
     * The type is scoped to key.
     */
    class Scoped(
        val key: TypeKey,
        val accessor: Accessor,
        val result: TypeResultRef,
    ) : TypeResult() {
        override val children
            get() = iterator { yield(result) }
    }

    /**
     * A constructor for the type.
     */
    class Constructor(
        val type: AstType,
        val scope: AstAnnotation?,
        val outerClass: TypeResultRef?,
        val parameters: Map<String, TypeResultRef>,
        val supportsNamedArguments: Boolean,
    ) : TypeResult() {
        override val children
            get() = iterator {
                outerClass?.let { yield(it) }
                yieldAll(parameters.values)
            }
    }

    /**
     * A container that holds the type (ex: Set or Map).
     */
    class Container(
        val creator: String,
        val args: List<TypeResultRef>,
    ) : TypeResult() {
        override val children
            get() = args.iterator()
    }

    class AssistedFactory(
        val type: AstType,
        val function: AstFunction,
        val result: TypeResultRef,
        val parameters: List<Pair<AstType, String>>,
    ) : TypeResult()

    class AssistedFunctionFactory(
        val type: AstType,
        val function: AstFunction,
        val parameters: List<Pair<AstType, String>>,
        val injectFunction: AstFunction,
        val injectFunctionParameters: Map<String, TypeResultRef>
    ) : TypeResult()

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
        val name: MemberName,
        val args: List<String>,
        val parameters: Map<String, TypeResultRef>,
    ) : TypeResult() {
        override val children
            get() = parameters.values.iterator()
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
     * A local var in the current scope, used for [LateInit].
     */
    class LocalVar(val name: String) : TypeResult()

    /**
     * A lazy type.
     */
    class Lazy(val result: TypeResultRef) : TypeResult() {

        override val children
            get() = iterator { yield(result) }
    }

    /**
     * Construct a reference using lateinit, this allows cycles when using lazy/functions for construction.
     */
    class LateInit(val name: String, val result: TypeResultRef) : TypeResult() {
        override val children
            get() = iterator { yield(result) }
    }
}