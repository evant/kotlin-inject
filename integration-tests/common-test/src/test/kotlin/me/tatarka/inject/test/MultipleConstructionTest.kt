package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isNotNull
import kotlin.test.Test

class MultipleConstructionTest {

    @Test
    fun generates_a_component_that_uses_a_getter_for_common_construction() {
        val component = CommonGetterComponent::class.create()

        assertThat(component.bar2).isNotNull()
        assertThat(component.bar3).isNotNull()
    }

    @Test
    fun generates_a_component_that_reuses_the_declared_getter_for_common_construction() {
        val component = ReuseExistingPropertyComponent::class.create()

        assertThat(component.bar).isNotNull()
        assertThat(component.bar2).isNotNull()
        assertThat(component.bar3).isNotNull()
    }

    @Test
    fun generates_a_component_that_provides_the_same_value_in_multiple_contexts() {
        val component = MultipleConstructionComponent::class.create()

        assertThat(component.foo).isNotNull()
        assertThat(component.bar).isNotNull()
        assertThat(component.set).containsOnly(
            Foo(),
            Bar(Foo()),
            Bar2(Bar(Foo())),
        )
    }

    @Test
    fun generates_a_component_that_provides_a_scoped_dependency_in_multiple_places() {
        val component = MultipleScopedConstructionComponent::class.create()

        assertThat(component.iBar).isNotNull()
        assertThat(component.bar).isNotNull()
        assertThat(component.bar2).isNotNull()
    }
}
