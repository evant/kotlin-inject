package me.tatarka.inject.compiler

import me.tatarka.kotlin.ast.AstType

fun <T> Iterable<T>.eqvItr(other: Iterable<T>, eqv: (T, T) -> Boolean): Boolean {
    val itr1 = iterator()
    val itr2 = other.iterator()
    while (itr1.hasNext()) {
        if (!itr2.hasNext()) {
            return false
        }
        val v1 = itr1.next()
        val v2 = itr2.next()
        if (!eqv(v1, v2)) {
            return false
        }
    }
    if (itr2.hasNext()) {
        return false
    }
    return true
}

fun <T : Any> T?.eqv(other: T?, eqv: (T, T) -> Boolean): Boolean =
    this == null && other == null ||
        this != null && other != null && eqv(this, other)

class HashCollector {
    var hash: Int = 1
        private set

    @Suppress("MagicNumber")
    fun hash(arg: Any?) {
        hash = 31 * hash + arg.hashCode()
    }
}

inline fun collectHash(collector: HashCollector = HashCollector(), block: HashCollector.() -> Unit): Int =
    collector.apply(block).hash

object EmptyIterator : Iterator<TypeResultRef> {
    override fun hasNext(): Boolean = false

    override fun next(): TypeResultRef = throw NoSuchElementException()
}

typealias TreeVisitor<T> = (T) -> Iterator<T>

fun <T> StringBuilder.renderTree(node: T, visitor: TreeVisitor<T>) {
    renderTree(node, visitor, "", true, this)
}

private fun <T> renderTree(node: T, visitor: TreeVisitor<T>, indent: String, out: StringBuilder) {
    val children = visitor(node)
    out.append("\n")
    while (children.hasNext()) {
        renderTree(children.next(), visitor, indent, !children.hasNext(), out)
    }
}

private fun <T> renderTree(node: T, visitor: TreeVisitor<T>, indent: String, isLast: Boolean, out: StringBuilder) {
    out.append(indent)
    val newIndent = if (isLast) {
        out.append("└")
        "$indent "
    } else {
        out.append("├")
        "$indent│"
    }
    renderTree(node, visitor, newIndent, out)
}

fun AstType.isSet(): Boolean = packageName == "kotlin.collections" && simpleName == "Set"
fun AstType.isMap(): Boolean = packageName == "kotlin.collections" && simpleName == "Map"
fun AstType.isPair(): Boolean = packageName == "kotlin" && simpleName == "Pair"
fun AstType.isFunctionOrTypeAliasOfFunction(): Boolean = isFunction() || isTypeAlias() &&
    resolvedType().isFunctionOrTypeAliasOfFunction()

tailrec fun AstType.fullyResolvedType(): AstType {
    check(isTypeAlias()) {
        "resolveToHighestTypeAlias should only be called on a typealias AstType"
    }

    val resolvedType = resolvedType()
    return if (resolvedType.isTypeAlias()) resolvedType.fullyResolvedType() else this
}
