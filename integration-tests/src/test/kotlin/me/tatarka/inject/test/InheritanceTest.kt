package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import kotlin.test.Test

interface ComponentInterface {
    val foo: Foo
}

@Component abstract class InterfaceComponent : ComponentInterface

interface GenericComponentInterface<T> {
    val foo: T
}

@Component abstract class GenericInterfaceComponent : GenericComponentInterface<Foo>

class InheritanceTest {
    @Test fun generates_a_component_that_provides_a_dep_defined_in_an_implemented_interface() {
        val component = InterfaceComponent::class.create()

        assertThat(component.foo).isNotNull()
    }

    @Test fun generates_a_component_that_provides_a_dep_defined_in_a_generic_implemented_interface() {
        val component = GenericInterfaceComponent::class.create()

        assertThat(component.foo).hasClass(Foo::class)
    }
}