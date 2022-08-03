package me.tatarka.inject.test.module

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope

@Inject
class ExternalFoo

@Scope
annotation class ExternalScope

@ExternalScope
@Inject
class ScopedExternalFoo : IExternalFoo

typealias externalFunction = () -> String

@Inject
@Suppress("FunctionOnlyReturningConstant", "UNUSED_PARAMETER")
fun externalFunction(foo: ExternalFoo): String = "external"

interface IExternalFoo

@Component
@ExternalScope
abstract class ExternalParentComponent {
    val ScopedExternalFoo.bind: IExternalFoo
        @Provides get() = this
}

@Component
abstract class ExternalChildComponent(@Component val parent: ExternalParentComponent = ExternalParentComponent::class.create())