package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class LazyComponentTest {

    @Test
    fun generates_a_component_that_provides_a_lazy_dep() {
        val component = LazyComponent::class.create()

        val lazyFoo = component.lazyFoo
        val createFoo = component.createFoo

        assertThat(lazyFoo.value).isSameInstanceAs(lazyFoo.value)
        assertThat(createFoo()).isNotSameInstanceAs(createFoo())
    }

    @Test
    fun generates_a_component_that_provides_a_lazy_dep_to_a_provides() {
        val component = LazyProvidesComponent::class.create()

        val lazyBar = component.lazyBar
        val createBar = component.createBar

        assertThat(lazyBar.foo.value).isSameInstanceAs(lazyBar.foo.value)
        assertThat(createBar.foo()).isNotSameInstanceAs(createBar.foo())
    }

    @Test
    fun generates_a_component_that_provides_a_function_that_provides_a_function() {
        val component = NestedFunctionComponent::class.create()

        assertThat(component.bar().foo()).isNotNull()
    }
}
