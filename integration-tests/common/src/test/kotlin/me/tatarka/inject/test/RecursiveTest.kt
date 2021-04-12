package me.tatarka.inject.test

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

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

class RecursiveTest {

    @Test
    fun generates_a_component_that_provides_data_recursively() {
        val component = CycleComponent::class.create()
        val bar = component.bar

        assertThat(bar).isSameAs(bar.foo().bar)
    }

    @Test
    fun generates_a_component_that_provides_a_lazy_dea_recursively() {
        val component = LazyCycleComponent::class.create()
        val bar = component.bar

        assertThat(bar).isSameAs(bar.foo.value.bar)
    }

    @Test
    fun generates_a_component_that_provides_a_function_dea_recursively() {
        val component = FunctionCycleComponent::class.create()
        val bar = component.bar

        assertThat(bar).isSameAs(bar.foo().bar)
    }

    @Test
    fun generates_a_component_that_provides_a_scoped_dependency_recursively() {
        val component = ScopedCycleComponent::class.create()
        val foo = component.foo.foo

        assertThat(foo).isSameAs(foo.bar.foo.value)
    }

    @Test
    fun generates_a_component_that_provides_a_nested_lazy_dependency_recursively() {
        val component = NestedLazyCycleComponent::class.create()
        val foo = component.foo
        val bar = foo.bar

        assertAll {
            assertThat(foo).isSameAs(foo.bar.baz.foo.value)
            assertThat(bar).isSameAs(foo.bar.baz.bar.value)
        }
    }
}