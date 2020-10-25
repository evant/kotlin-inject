package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.KModifier

enum class AstVisibility { PUBLIC, PRIVATE, PROTECTED, INTERNAL }

fun AstVisibility.toModifier(): KModifier = when (this) {
    AstVisibility.PUBLIC -> KModifier.PUBLIC
    AstVisibility.PRIVATE -> KModifier.PRIVATE
    AstVisibility.PROTECTED -> KModifier.PROTECTED
    AstVisibility.INTERNAL -> KModifier.INTERNAL
}