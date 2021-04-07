package me.tatarka.inject.compiler

/**
 * Represents a function/property to generate.
 */
class MethodEntry private constructor(
    val name: String,
    val returnType: AstType,
    val isProperty: Boolean,
    val isPrivate: Boolean,
    val isOverride: Boolean,
    val isSuspend: Boolean,
    val typeResult: TypeResultRef,
) {
    companion object {
        /**
         * A provider, ex:
         * ```
         * override fun provideFoo(bar: Bar): Foo = ...
         * ```
         */
        fun provider(method: AstMethod, returnType: AstType, typeResult: TypeResultRef) = MethodEntry(
            name = method.name,
            returnType = returnType,
            isProperty = method is AstProperty,
            isPrivate = false,
            isOverride = true,
            isSuspend = method is AstFunction && method.isSuspend,
            typeResult = typeResult,
        )

        /**
         * A private getter ex:
         * ```
         * private val _foo: Foo
         *   get() = ...
         * ```
         */
        fun privateGetter(name: String, returnType: AstType, typeResult: TypeResultRef) = MethodEntry(
            name = name,
            returnType = returnType,
            isProperty = true,
            isPrivate = true,
            isOverride = false,
            isSuspend = false,
            typeResult = typeResult,
        )
    }
}