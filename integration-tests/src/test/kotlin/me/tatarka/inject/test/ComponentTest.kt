package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import kotlin.test.Test

@Component abstract class Component1 {
    abstract val foo: Foo
}


@Component abstract class Component2 {
    abstract val bar: Bar
}

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
}