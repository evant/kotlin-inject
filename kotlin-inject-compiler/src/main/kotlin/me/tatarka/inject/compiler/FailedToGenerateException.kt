package me.tatarka.inject.compiler

import me.tatarka.inject.compiler.ast.AstElement

class FailedToGenerateException(message: String, val element: AstElement?) : Exception(message)
