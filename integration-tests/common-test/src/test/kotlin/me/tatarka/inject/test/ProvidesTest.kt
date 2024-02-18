package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test

class ProvidesTest {

    @Test
    fun generates_a_component_that_provides_a_dep_from_a_function() {
        val component = ProvidesFunctionComponent::class.create()

        component.foo
        assertThat(component.providesCalled).isTrue()
    }

    @Test
    fun generates_a_component_that_provides_a_dep_from_a_function_with_arg() {
        val component = ProvidesFunctionArgComponent::class.create()
        val foo: ProvidesFoo = component.foo

        assertThat(foo.bar).isNotNull()
        assertThat(component.providesCalled).isTrue()
    }

    @Test
    fun generates_a_component_that_provides_a_dep_from_a_val() {
        val component = ProvidesValComponent::class.create()

        component.foo
        assertThat(component.providesCalled).isTrue()
    }

    @Test
    fun generates_a_component_that_provides_a_deb_from_an_extension_function() {
        val component = ProvidesExtensionFunctionComponent::class.create()

        assertThat(component.foo.bar).isNotNull()
        assertThat(component.providesCalled).isTrue()
    }

    @Test
    fun generates_a_component_that_provides_a_deb_from_an_extension_val() {
        val component = ProvidesExtensionValComponent::class.create()

        assertThat(component.foo.bar).isNotNull()
        assertThat(component.providesCalled).isTrue()
    }

    @Test
    fun generates_a_component_that_provides_a_dep_from_a_constructor_val() {
        val foo = ProvidesFoo()
        val component = ProvidesValConstructorComponent::class.create(foo)

        assertThat(component.foo).isSameInstanceAs(foo)
    }

    @Test
    fun generates_a_component_that_provides_from_functions_with_the_same_name() {
        val component = ProvidesOverloadsComponent::class.create()

        assertThat(component.foo1).isNotNull()
        assertThat(component.foo2).isNotNull()
        assertThat(component.foo3).isNotNull()
        assertThat(component.foo4).isNotNull()
    }

    @Test
    fun generates_a_component_that_provides_basic_types() {
        val component = ProvidesBasicTypes::class.create()

        assertThat(component.basicType.boolean).isTrue()
        assertThat(component.basicType.byte).isEqualTo(1.toByte())
        assertThat(component.basicType.char).isEqualTo('a')
        assertThat(component.basicType.short).isEqualTo(2.toShort())
        assertThat(component.basicType.int).isEqualTo(3)
        assertThat(component.basicType.long).isEqualTo(4L)
        assertThat(component.basicType.float).isEqualTo(5f)
        assertThat(component.basicType.double).isEqualTo(6.0)
        assertThat(component.basicType.string).isEqualTo("b")
        assertThat(component.basicType.booleanArray.toTypedArray()).containsExactly(true)
        assertThat(component.basicType.stringArray).containsExactly("c")
        assertThat(component.basicType.intFoo.int).isEqualTo(3)
        assertThat(component.basicType.intArrayFoo.intArray.toTypedArray()).containsExactly(7)
        assertThat(component.basicType.stringArrayFoo.stringArray).containsExactly("c")
    }

    @Test
    fun generates_a_component_that_provides_val_constructor_basic_types() {
        val component = ProvidesValConstructorBasicTypes::class.create(
            true,
            "a"
        )

        assertThat(component.basicType.boolean).isTrue()
        assertThat(component.basicType.string).isEqualTo("a")
    }

    @Test
    fun generates_a_component_that_references_an_inner_class() {
        val component = ProvidesInnerClassComponent::class.create()

        assertThat(component.fooFactory).isNotNull()
    }

    @Test
    fun generates_a_component_where_a_provides_fun_optimizes_to_references_another_provides_fun() {
        val component = OptimizesProvides::class.create()

        assertThat(component.provideFoo()).isNotNull()
        assertThat(component.provideBar()).isNotNull()
    }
}
