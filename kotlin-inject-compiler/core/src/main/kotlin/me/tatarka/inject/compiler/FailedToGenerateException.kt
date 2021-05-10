package me.tatarka.inject.compiler

class FailedToGenerateException(message: String, val element: AstElement? = null) :
    Exception(message)


class ErrorTypeException: Exception()