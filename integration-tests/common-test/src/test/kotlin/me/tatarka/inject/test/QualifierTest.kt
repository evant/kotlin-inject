package me.tatarka.inject.test

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class QualifierTest {

    @Test
    fun generates_a_component_that_provides_different_values_based_on_the_type_alias_name() {
        val component = ProvidesAliasedComponent::class.create()

        assertAll {
            assertThat(component.foo1.name).isEqualTo("1")
            assertThat(component.foo2.name).isEqualTo("2")
        }
    }

    @Test
    fun generates_a_component_that_constructs_different_values_based_on_the_type_alias_name() {
        val component = ConstructorAliasedComponent::class.create()

        assertAll {
            assertThat(component.aliasedFoo.foo1.name).isEqualTo("1")
            assertThat(component.aliasedFoo.foo2.name).isEqualTo("2")
        }
    }

    @Test
    fun generates_a_component_that_constructs_different_scoped_values_based_on_the_type_alias_name() {
        val component = ScopedConstructorAliasedComponent::class.create()

        assertAll {
            assertThat(component.aliasedFoo.foo1.name).isEqualTo("1")
            assertThat(component.aliasedFoo.foo2.name).isEqualTo("2")
        }
    }
}
