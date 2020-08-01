package me.tatarka.inject.compiler

class FailedToGenerateException(message: String, val element: AstElement) : Exception(message)
