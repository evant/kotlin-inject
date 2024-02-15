package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides

@Inject
class FooWithDefault(
    override val name: String = "default",
    override val fn: () -> String = { "default" },
    override val lazy: Lazy<String> = lazy { "default" },
) : IFooWithDefault

interface IFooWithDefault {
    val name: String
    val fn: () -> String
    val lazy: Lazy<String>
}

typealias fooWithDefaultFun = () -> IFooWithDefault

@Inject
fun fooWithDefaultFun(
    name: String = "default",
    fn: () -> String = { "default" },
    lazy: Lazy<String> = lazy { "default" },
): IFooWithDefault {
    return FooWithDefault(name, fn, lazy)
}

@Component
abstract class UseDefaultComponent {
    abstract val foo: FooWithDefault
    abstract val iFoo: IFooWithDefault
    abstract val fooFun: fooWithDefaultFun

    @Provides
    fun iFoo(
        name: String = "default",
        fn: () -> String = { "default" },
        lazy: Lazy<String> = lazy { "default" },
    ): IFooWithDefault = FooWithDefault(name, fn, lazy)
}

@Component
abstract class OverrideDefaultComponent {
    abstract val foo: FooWithDefault
    abstract val iFoo: IFooWithDefault
    abstract val fooFun: fooWithDefaultFun

    val name: String
        @Provides get() = "override"

    @Provides
    fun iFoo(
        name: String = "default",
        fn: () -> String = { "default" },
        lazy: Lazy<String> = lazy { "default" },
    ): IFooWithDefault = FooWithDefault(name, fn, lazy)
}
