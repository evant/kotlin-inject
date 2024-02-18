package me.tatarka.inject.test

import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.test.module.externalFunction

@Component
abstract class FunctionInjectionComponent {
    abstract val bar: bar

    abstract val externalFunction: externalFunction
}

typealias F = String

typealias foo = (F) -> String

@Inject
@Suppress("UNUSED_PARAMETER")
fun foo(dep: Foo, @Assisted arg: F): String = arg

typealias bar = () -> String

@Inject
fun bar(foo: foo): String = foo("test")

typealias receiverFun = String.(arg: NamedFoo) -> String

@Inject
@Suppress("UNUSED_PARAMETER")
fun String.receiverFun(dep: Foo, @Assisted arg: NamedFoo): String = this

@Component
@CustomScope
abstract class ReceiverFunctionInjectionComponent {
    abstract val receiverFun: receiverFun
}
