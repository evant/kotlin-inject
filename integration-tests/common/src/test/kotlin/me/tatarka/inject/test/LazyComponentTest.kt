package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

@Inject class LazyFoo

class LazyBar(val foo: Lazy<LazyFoo>)

@Component abstract class LazyComponent {
    abstract val foo: Lazy<LazyFoo>
}

@Component abstract class LazyProvidesComponent {
    abstract val bar: LazyBar

    @Provides
    fun bar(foo: Lazy<LazyFoo>) = LazyBar(foo)
}

class LazyComponentTest {

    @Test
    fun generates_a_component_that_provides_a_lazy_dep() {
        val component = LazyComponent::class.create()

        val foo = component.foo

        assertThat(foo.value).isSameAs(foo.value)
    }

    @Test
    fun generates_a_component_that_provides_a_lazy_dep_to_a_provides() {
        val component = LazyProvidesComponent::class.create()

        val bar = component.bar

        assertThat(bar.foo.value).isSameAs(bar.foo.value)
    }
}
