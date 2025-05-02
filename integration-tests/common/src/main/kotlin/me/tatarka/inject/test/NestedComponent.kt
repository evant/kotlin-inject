package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@Component abstract class ParentComponent {
    abstract val parentNamedFoo: NamedFoo

    @Provides
    protected fun foo() = NamedFoo("parent")

    @Provides
    fun bar() = NamedBar("parent")

    val Foo.binds: IFoo
        @Provides get() = this

    val NamedBar.binds: INamedBar
        @Provides get() = this
}

@Component abstract class SimpleChildComponent1(@Component val parent: ParentComponent) {
    abstract val namedFoo: NamedFoo

    abstract val namedBar: INamedBar

    val BarImpl.binds: IBar
        @Provides get() = this

    abstract val foo: IFoo
}

@Component abstract class SimpleChildComponent2(@Component val parent: SimpleChildComponent1) {
    abstract val namedFoo: NamedFoo

    abstract val namedBar: INamedBar

    abstract val foo: IFoo

    abstract val bar: IBar
}
