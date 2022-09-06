package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

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

class LazyComponentTest {

    @Test
    fun generates_a_component_that_provides_a_lazy_dep() {
        val component = LazyComponent::class.create()

        val lazyFoo = component.lazyFoo
        val createFoo = component.createFoo

        assertThat(lazyFoo.value).isSameAs(lazyFoo.value)
        assertThat(createFoo).isNotSameAs(createFoo)
    }

    @Test
    fun generates_a_component_that_provides_a_lazy_dep_to_a_provides() {
        val component = LazyProvidesComponent::class.create()

        val lazyBar = component.lazyBar
        val createBar = component.createBar

        assertThat(lazyBar.foo.value).isSameAs(lazyBar.foo.value)
        assertThat(createBar.foo()).isNotSameAs(createBar.foo())
    }

    @Test
    fun generates_a_component_that_provides_a_function_that_provides_a_function() {
        val component = NestedFunctionComponent::class.create()

        assertThat(component.bar().foo()).isNotNull()
    }
}
