package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlin.test.Test

class AbstractComponentTest {
    @Test
    fun generates_a_component_that_provides_a_dep_from_an_abstract_parent_component() {
        val parent1 = ParentComponentImpl1::class.create()
        val parent2 = ParentComponentImpl2::class.create()
        val component1 = AbstractParentChildComponent::class.create(parent1)
        val component2 = AbstractParentChildComponent::class.create(parent2)

        assertThat(component1.foo.name).isEqualTo("parent1")
        assertThat(component1.bar.name).isEqualTo("parent1")
        assertThat(component2.foo.name).isEqualTo("parent2")
        assertThat(component2.bar.name).isEqualTo("parent2")
    }

    @Test
    fun generates_a_component_that_provides_a_dep_from_an_abstract_scoped_component() {
        val parent1 = ScopedParentComponentImpl1::class.create()
        val parent2 = ScopedParentComponentImpl2::class.create()
        val component1 = ScopedAbstractParentChildComponent::class.create(parent1)
        val component2 = ScopedAbstractParentChildComponent::class.create(parent2)

        assertThat(component1.bar).isNotNull()
        assertThat(component2.bar).isNotNull()
    }
}
