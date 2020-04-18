package me.tatarka.inject.test

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Component
import kotlin.test.BeforeTest
import kotlin.test.Test

@Inject class Foo : IFoo

@Component abstract class Component1 {
    abstract val foo: Foo
}

@Inject class Bar(val foo: Foo)

@Component abstract class Component2 {
    abstract val bar: Bar
}

interface IFoo

class ComponentTest {

    @BeforeTest
    fun setup() {
        customScopeBarConstructorCount = 0
    }

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
    fun generates_a_component_that_constructs_different_values_based_on_the_named_qualifier() {
        val component = ConstructorAliasedComponent::class.create()

        assertAll {
            assertThat(component.aliasedFoo.foo1.name).isEqualTo("1")
            assertThat(component.aliasedFoo.foo2.name).isEqualTo("2")
        }
    }
}