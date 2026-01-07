package me.tatarka.inject.compiler

import me.tatarka.kotlin.ast.AstAnnotation
import me.tatarka.kotlin.ast.AstType

class TypeKey(val type: AstType, val memberQualifier: AstAnnotation? = null) {

    val qualifier = memberQualifier ?: type.typeQualifierAnnotations().firstOrNull()

    override fun equals(other: Any?): Boolean {
        if (other !is TypeKey) return false
        return qualifier == other.qualifier && type == other.type &&
            type.arguments.eqvItr(other.type.arguments, ::qualifiersEquals)
    }

    override fun hashCode(): Int {
        return collectHash {
            hash(type)
            hash(qualifier)
        }
    }

    override fun toString(): String = StringBuilder().apply {
        if (qualifier != null) {
            append(qualifier)
            append(" ")
        }
        append(type)
    }.toString()

    companion object {
        private fun qualifiersEquals(left: AstType, right: AstType): Boolean {
            val leftAnnotations = left.typeQualifierAnnotations().asIterable()
            val rightAnnotations = right.typeQualifierAnnotations().asIterable()
            return leftAnnotations.eqvItr(rightAnnotations, AstAnnotation::equals) &&
                left.arguments.eqvItr(right.arguments, ::qualifiersEquals)
        }
    }
}