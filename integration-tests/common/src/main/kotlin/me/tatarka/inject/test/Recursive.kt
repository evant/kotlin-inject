package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides

class LazyCycleFoo(val bar: LazyCycleBar)

class LazyCycleBar(val foo: Lazy<LazyCycleFoo>)

@Inject
class FBar(val foo: () -> FFoo)

@Inject
class FFoo(val bar: FBar)

@Inject
class CycleFoo(val bar: CycleBar)

@Inject
class CycleBar(val foo: () -> CycleFoo)

@Inject
class CycleBaz(val foo: CycleFoo)

@Inject
@CustomScope
data class CycleScopedFoo(val bar: CycleScopedBar)

@Inject
data class CycleScopedBar(val foo: Lazy<CycleScopedFoo>)

@Inject
class ScopedCycle(val foo: CycleScopedFoo)

@Inject
class NestedLazyBar(val baz: NestedLazyBaz)

@Inject
class NestedLazyBaz(val foo: Lazy<NestedLazyFoo>, val bar: Lazy<NestedLazyBar>)

@Inject
class NestedLazyFoo(val bar: NestedLazyBar)

@Component
abstract class CycleComponent {
    abstract val bar: CycleBar
}

@Component
abstract class LazyCycleComponent {
    abstract val bar: LazyCycleBar

    @Provides
    fun bar(foo: Lazy<LazyCycleFoo>) = LazyCycleBar(foo)

    @Provides
    fun foo(bar: LazyCycleBar) = LazyCycleFoo(bar)
}

@Component
abstract class FunctionCycleComponent {
    abstract val bar: FBar

    @Provides
    fun bar(foo: () -> FFoo) = FBar(foo)

    @Provides
    fun foo(bar: FBar) = FFoo(bar)
}

@Component
@CustomScope
abstract class ScopedCycleComponent {
    abstract val foo: ScopedCycle
}

@Component
abstract class NestedLazyCycleComponent {
    abstract val foo: NestedLazyFoo
}

@Component
abstract class OptimizedCycleComponent {
    abstract val foo: CycleFoo

    abstract val bar: CycleBar

    abstract val baz: CycleBaz
}

@CustomScope
@Inject
class ScopeBar(val foo: ScopeFoo)

@Inject
class ScopeFoo(val bar: Lazy<ScopeBar>)

@Component
@CustomScope
abstract class ParentCycleComponent

@Component
abstract class ChildCycleComponent(
    @Component val parent: ParentCycleComponent,
) {
    abstract val foo: ScopeFoo
}
