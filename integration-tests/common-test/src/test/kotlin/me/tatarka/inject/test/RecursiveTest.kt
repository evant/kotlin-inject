package me.tatarka.inject.test

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class RecursiveTest {

    @Test
    fun generates_a_component_that_provides_data_recursively() {
        val component = CycleComponent::class.create()
        val bar = component.bar

        assertThat(bar).isSameInstanceAs(bar.foo().bar)
    }

    @Test
    fun generates_a_component_that_provides_a_lazy_dea_recursively() {
        val component = LazyCycleComponent::class.create()
        val bar = component.bar

        assertThat(bar).isSameInstanceAs(bar.foo.value.bar)
    }

    @Test
    fun generates_a_component_that_provides_a_lazy_scoped_dea_recursively() {
        val component: LazyScopedCycleComponent = LazyScopedCycleComponent::class.create()
        val foo = component.foo

        assertThat(foo).isSameInstanceAs(foo.lazy.value.foo.value)
    }

    @Test
    fun generates_a_component_that_provides_a_function_dea_recursively() {
        val component = FunctionCycleComponent::class.create()
        val bar = component.bar

        assertThat(bar).isSameInstanceAs(bar.foo().bar)
    }

    @Test
    fun generates_a_component_that_provides_a_scoped_dependency_recursively() {
        val component = ScopedCycleComponent::class.create()
        val foo = component.foo.foo

        assertThat(foo).isSameInstanceAs(foo.bar.foo.value)
    }

    @Test
    fun generates_a_component_that_provides_a_nested_lazy_dependency_recursively() {
        val component = NestedLazyCycleComponent::class.create()
        val foo = component.foo
        val bar = foo.bar

        assertAll {
            assertThat(foo).isSameInstanceAs(foo.bar.baz.foo.value)
            assertThat(bar).isSameInstanceAs(foo.bar.baz.bar.value)
        }
    }

    @Test
    fun generates_a_component_that_provides_date_recursively_in_multiple_places() {
        val component = OptimizedCycleComponent::class.create()
        val foo = component.foo
        val bar = component.bar
        val baz = component.baz

        assertAll {
            assertThat(foo).isSameInstanceAs(foo.bar.foo())
            assertThat(bar).isSameInstanceAs(bar.foo().bar)
            assertThat(baz.foo).isSameInstanceAs(baz.foo.bar.foo())
        }
    }

    @Test
    fun generates_a_component_with_a_scoped_parent_dependency_recursively() {
        val component = ChildCycleComponent::class.create(ParentCycleComponent::class.create())
        val foo = component.foo
        val bar = foo.bar.value

        assertThat(foo).isSameInstanceAs(foo.bar.value.foo)
        assertThat(bar).isSameInstanceAs(bar.foo.bar.value)
    }
}
