package me.tatarka.inject.compiler

import me.tatarka.kotlin.ast.AstAnnotation
import me.tatarka.kotlin.ast.AstType

class TypeKey(val type: AstType, val qualifier: AstAnnotation? = null) {

    override fun equals(other: Any?): Boolean {
        if (other !is TypeKey) return false
        return qualifier == other.qualifier && type == other.type
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
}