package me.tatarka.inject.sample

import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import me.tatarka.inject.annotations.Singleton

@Inject
@Singleton
class Foo() : IFoo

interface IFoo

class Bar()

@Inject
class Baz(foo: IFoo, bar: Bar)

@Module
@Singleton
abstract class MyModule {
    abstract val baz: Baz

    protected val Foo.binds: IFoo get() = this

    protected fun bar() = Bar()
}

fun main() {
    val module: MyModule = MyModule::class.create()
    println(module.baz)
}

