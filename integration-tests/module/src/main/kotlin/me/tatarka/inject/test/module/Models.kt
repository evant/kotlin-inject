package me.tatarka.inject.test.module

import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Scope

@Inject
class ExternalFoo

@Scope
annotation class ExternalScope

@ExternalScope
@Inject
class ScopedExternalFoo

typealias externalFunction = () -> String

@Inject
@Suppress("FunctionOnlyReturningConstant")
fun externalFunction(foo: ExternalFoo): String = "external"