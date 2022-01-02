package me.tatarka.inject.compiler

import me.tatarka.kotlin.ast.AstElement

class FailedToGenerateException(message: String, val element: AstElement? = null) :
    Exception(message)
