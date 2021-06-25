package me.tatarka.inject.internal

@Suppress("VariableNaming")
interface ScopedComponent {
    val _scoped: LazyMap
}