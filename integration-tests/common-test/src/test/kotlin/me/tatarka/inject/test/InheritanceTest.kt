package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class InheritanceTest {
    @Test
    fun generates_a_component_that_provides_a_dep_defined_in_an_implemented_interface() {
        val component = InterfaceComponent::class.create()

        assertThat(component.foo).isNotNull()
    }

    @Test
    fun generates_a_component_that_provides_a_dep_defined_in_an_indirect_interface() {
        val component = ProvidesIndirectComponent::class.create()

        assertThat(component.foo).isNotNull()
    }

    @Test
    fun generates_a_component_that_ignores_abstract_superclass_methods_that_are_overridden_by_the_component() {
        val foo = Foo()
        val bar = Bar(foo)
        val component = InterfaceComponentWithIdenticalProvides::class.create(foo, bar)

        assertThat(component.foo).isSameInstanceAs(foo)
        assertThat(component.bar).isSameInstanceAs(bar)
    }

    @Test
    fun generates_a_component_that_provides_a_dep_defined_in_a_generic_implemented_interface() {
        val component = GenericInterfaceComponent::class.create()

        assertThat(component.genericFoo).hasClass(Foo::class)
    }

    @Test
    fun generates_a_component_that_provides_a_scoped_dep_defined_in_an_implemented_interface() {
        val component = ProvidesScopedInterfaceComponent::class.create()

        assertThat(component.foo).isSameInstanceAs(component.foo)
    }

    @Test
    fun generates_a_component_that_inherits_multiple_interfaces_with_the_same_signature() {
        val component = DuplicateDeclarationComponent::class.create()

        assertThat(component.bar).isEqualTo(component.bar2())
    }

    @Test
    fun generates_a_component_that_inherits_an_interface_with_a_covariant_signature() {
        val appComponent = InheritedAppComponent::class.create()
        val component = InheritedSessionComponent::class.create(
            appComponent = appComponent
        )

        assertThat(component.appComponent).isSameInstanceAs(appComponent)
    }
}
