package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@Component abstract class SuspendFunctionComponent {
    abstract val foo: () -> IFoo

    abstract val suspendFoo: suspend () -> IFoo

    val suspendFooFunction: suspend () -> IFoo
        @Provides get() = { Foo() }

    val fooFunction: () -> IFoo
        @Provides get() = { Foo() }
}

@Component abstract class SuspendProviderComponent {
    abstract suspend fun suspendFoo(): IFoo

    @Provides suspend fun suspendProvides(): IFoo = Foo()
}
