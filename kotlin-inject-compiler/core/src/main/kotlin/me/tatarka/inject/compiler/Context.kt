package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.NameAllocator
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstProvider
import me.tatarka.kotlin.ast.AstType

/**
 * A context to find types in. Holds enough info to figure out how to obtain the type.
 */
data class Context(
    val provider: AstProvider,
    val className: String,
    val types: TypeCollector.Result,
    val scopeComponent: AstClass?,
    val scopeInterface: AstClass?,
    val nameAllocator: NameAllocator,
    val args: List<Pair<AstType, String>> = emptyList(),
    val skipScoped: AstType? = null,
    val skipProvider: AstType? = null,
) {
    fun withoutScoped(scoped: AstType, scopeComponent: AstClass) =
        copy(skipScoped = scoped, scopeComponent = scopeComponent)

    fun withoutProvider(provider: AstType) = copy(skipProvider = provider)

    fun withArgs(args: List<Pair<AstType, String>>) = copy(args = args)

    fun withoutArgs() = copy(args = emptyList())

    fun withTypes(types: TypeCollector.Result) = if (this.types === types) {
        this
    } else {
        copy(types = types)
    }

    fun copyNameAllocator() = copy(nameAllocator = nameAllocator.copy())
}