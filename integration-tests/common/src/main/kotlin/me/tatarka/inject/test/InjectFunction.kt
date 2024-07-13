package me.tatarka.inject.test

import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.AssistedFactory
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.test.module.externalFunction

@Component
abstract class FunctionInjectionComponent {
    abstract val bar: bar

    abstract val externalFunction: externalFunction

    abstract val fooFactory: FooFactory

    abstract val barFactory: BarFactory

    abstract val externalFunctionFactory: ExternalFunctionFactory

    abstract val objectFunctionFactory: ObjectFunctionFactory
}

@AssistedFactory("me.tatarka.inject.test.ObjectWithFunction.objectFunction")
interface ObjectFunctionFactory {
    operator fun invoke(name: String): SomethingNotInjectable
}

class SomethingNotInjectable(val name: String)

object ObjectWithFunction {
    fun objectFunction(@Assisted name: String): SomethingNotInjectable = SomethingNotInjectable(name)
}

@AssistedFactory("me.tatarka.inject.test.module.externalFunction")
interface ExternalFunctionFactory {
    operator fun invoke(): String
}

@AssistedFactory("foo")
interface FooFactory {
    operator fun invoke(arg: F): String
}

typealias F = String

typealias foo = (F) -> String

@Inject
@Suppress("UNUSED_PARAMETER")
fun foo(dep: Foo, @Assisted arg: F): String = arg

@AssistedFactory("bar")
interface BarFactory {
    operator fun invoke(): String
}

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
