package me.tatarka.inject.compiler

import java.util.*

class TypeKey(val type: AstType, val qualifier: Any? = null) {

    override fun equals(other: Any?): Boolean {
        if (other !is TypeKey) return false
        return qualifier == other.qualifier && type == other.type
    }

    override fun hashCode(): Int {
        return Objects.hash(type, qualifier)
    }

    override fun toString(): String = StringBuilder().apply {
        if (qualifier != null) {
            append(qualifier)
            append(" ")
        }
        append(type)
    }.toString()
}