package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides

class Inner {
    @Inject
    class Bar(val foo: Foo)
}

@Inject
class Bar4(val bar: Inner.Bar)

@Inject
class Bar5(val bar: Inner.Bar)

@Component
abstract class CommonGetterComponent {
    abstract val bar2: Bar2

    abstract val bar3: Bar3

    abstract val bar4: Bar4

    abstract val bar5: Bar5
}

@Component
abstract class ReuseExistingPropertyComponent {
    abstract val bar: Bar

    abstract val bar2: Bar2

    abstract val bar3: Bar3
}

@Component
abstract class MultipleConstructionComponent {
    abstract val foo: Foo

    abstract val bar: Bar

    abstract val bar2: Bar2

    abstract val set: Set<IFoo>

    val Foo.bind: IFoo
        @Provides @IntoSet get() = this

    val Bar.bind: IFoo
        @Provides @IntoSet get() = this

    val Bar2.bind: IFoo
        @Provides @IntoSet get() = this
}

@Component
@CustomScope
abstract class MultipleScopedConstructionComponent {

    abstract val iBar: IBar

    abstract val bar: BarImpl

    abstract val bar2: BarImpl2

    @Provides
    fun providesIBar(
        @Suppress("UNUSED_PARAMETER")
        foo: IFoo,
    ): IBar = object : IBar {}

    val Foo.bind: IFoo
        @Provides @CustomScope get() = this
}
