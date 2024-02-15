package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides

@Inject
class LazyFoo

class LazyBar(val foo: Lazy<LazyFoo>)

class CreateBar(val foo: () -> LazyFoo)

@Component
abstract class LazyComponent {
    abstract val createFoo: () -> LazyFoo

    abstract val lazyFoo: Lazy<LazyFoo>
}

@Component
abstract class LazyProvidesComponent {
    abstract val lazyBar: LazyBar

    abstract val createBar: CreateBar

    @Provides
    fun lazyBar(foo: Lazy<LazyFoo>): LazyBar = LazyBar(foo)

    @Provides
    fun createBar(foo: () -> LazyFoo): CreateBar = CreateBar(foo)
}

@Inject
class FunctionBar(val foo: () -> Foo)

@Component
abstract class NestedFunctionComponent {
    abstract val bar: () -> FunctionBar
}
