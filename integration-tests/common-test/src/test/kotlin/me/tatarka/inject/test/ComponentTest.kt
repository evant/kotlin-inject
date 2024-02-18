package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import kotlin.test.Test

class ComponentTest {

    @Test
    fun generates_a_component_that_provides_a_dep_with_no_arguments() {
        val component = Component1::class.create()

        assertThat(component.foo).isNotNull()
    }

    @Test
    fun generates_a_component_that_provides_a_dep_with_an_argument() {
        val component = Component2::class.create()

        assertThat(component.bar).isNotNull()
        assertThat(component.bar.foo).isNotNull()
    }

    @Test
    fun generates_a_component_from_an_interface_that_provides_a_dep_with_an_argument() {
        val component = Component3::class.create()

        assertThat(component.bar).isNotNull()
        assertThat(component.bar.foo).isNotNull()
    }

    @Test
    fun generates_a_component_with_a_function_provider() {
        val component = Component4::class.create()

        assertThat(component.foo()).isNotNull()
    }
}
