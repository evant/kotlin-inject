package me.tatarka.inject.test

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

typealias NamedFoo1 = NamedFoo
typealias NamedFoo2 = NamedFoo

@Component abstract class ProvidesAliasedComponent {
    abstract val foo1: NamedFoo1

    abstract val foo2: NamedFoo2

    @Provides fun foo1(): NamedFoo1 = NamedFoo("1")
    @Provides fun foo2(): NamedFoo2 = NamedFoo("2")
}

@Inject class AliasedFoo(val foo1: NamedFoo1, val foo2: NamedFoo2)

@Component abstract class ConstructorAliasedComponent {
    abstract val aliasedFoo: AliasedFoo

    @Provides fun foo1(): NamedFoo1 = NamedFoo("1")
    @Provides fun foo2(): NamedFoo2 = NamedFoo("2")
}

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
}