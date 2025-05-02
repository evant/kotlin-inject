package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlin.test.Test

class NestedComponentTest {
    @Test
    fun generates_a_component_that_provides_a_dep_from_a_parent_component() {
        val parent = ParentComponent::class.create()
        val component = SimpleChildComponent1::class.create(parent)

        assertThat(component.parent.parentNamedFoo.name).isEqualTo("parent")
        assertThat(component.namedFoo.name).isEqualTo("parent")
        assertThat(component.namedBar.name).isEqualTo("parent")
        assertThat(component.foo).isNotNull()
    }

    @Test
    fun generates_a_component_that_provide_a_dep_from_2_parents_up() {
        val parent = ParentComponent::class.create()
        val child1 = SimpleChildComponent1::class.create(parent)
        val component = SimpleChildComponent2::class.create(child1)

        assertThat(component.parent.parent.parentNamedFoo.name).isEqualTo("parent")
        assertThat(component.namedFoo.name).isEqualTo("parent")
        assertThat(component.namedBar.name).isEqualTo("parent")
        assertThat(component.foo).isNotNull()
        assertThat(component.bar).isNotNull()
    }
}
