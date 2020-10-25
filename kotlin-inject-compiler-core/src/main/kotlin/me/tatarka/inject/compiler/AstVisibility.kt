package me.tatarka.inject.compiler

enum class AstVisibility { PUBLIC, PRIVATE, PROTECTED, INTERNAL }

interface VisibleElement {
    val visibility: AstVisibility
}