package me.tatarka.inject.compiler

import java.util.*

class TypeKey(val type: AstType) {
    val qualifier = type.typeAliasName

    override fun equals(other: Any?): Boolean {
        if (other !is TypeKey) return false
        return qualifier == other.qualifier && type == other.type
    }

    override fun hashCode(): Int {
        return Objects.hash(type, qualifier)
    }

    override fun toString(): String {
        return qualifier ?: type.toString()
    }
}