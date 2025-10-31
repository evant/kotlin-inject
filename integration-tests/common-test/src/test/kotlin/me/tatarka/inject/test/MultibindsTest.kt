package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import kotlin.test.Test

class MultibindsTest {

    @Test
    fun generates_a_component_that_provides_multiple_items_into_a_set() {
        val component = SetComponent::class.create()

        assertThat(component.items).containsOnly(FooValue("1"), FooValue("2"))
        assertThat(component.funItems.map { it() }).containsOnly(FooValue("1"), FooValue("2"))
        assertThat(component.lazyItems.map { it.value }).containsOnly(FooValue("1"), FooValue("2"))
    }

    @Test
    fun generates_a_component_that_provides_multiple_items_into_a_map() {
        val component = DynamicKeyComponent::class.create()

        assertThat(component.items).containsOnly(
            "1" to FooValue("1"),
            "2" to FooValue("2")
        )
    }

    @Test
    fun generates_a_child_component_that_provides_multiple_items_into_a_set() {
        val component = ChildSetComponent::class.create(ParentSetComponent::class.create())

        assertThat(component.items).containsOnly(
            Foo(),
            Bar(Foo()),
            Foo3()
        )
    }

    @Test
    fun generates_a_component_that_provides_multiple_items_using_a_type_alias_into_a_map() {
        val component = TypeAliasMapComponent::class.create()

        assertThat(component.items).containsOnly(
            "1" to FooValue("1"),
            "2" to FooValue("2")
        )
    }

    @Test
    fun generates_a_component_that_provides_set_with_an_assisted_arg() {
        val component = AssistedSetComponent::class.create()

        assertThat(component.items.map { it("arg") }).containsOnly(
            FooValue("arg1"), FooValue("arg2")
        )
    }

    @Test
    fun generates_a_component_with_different_sets_for_different_typealias() {
        val component = TypeAliasSetComponent::class.create()

        assertThat(component.stringItems).containsOnly("string")
        assertThat(component.myStringItems).containsOnly("myString")
    }

    @Test
    fun generates_a_component_with_different_maps_for_different_key_typealias() {
        val component = TypeAliasKeyMapComponent::class.create()

        assertThat(component.stringMap).containsOnly("1" to "string")
        assertThat(component.myStringMap).containsOnly("1" to "myString")
    }

    @Test
    fun generate_a_component_with_qualified_multibindings() {
        val component: MultibindWithQualifiersComponent = MultibindWithQualifiersComponent::class.create()

        assertThat(component.set1).containsExactlyInAnyOrder("1")
        assertThat(component.set2).containsExactlyInAnyOrder("2")
        assertThat(component.map1.toList()).containsExactlyInAnyOrder("1" to "2")
        assertThat(component.map2.toList()).containsExactlyInAnyOrder("2" to "1")
    }
}
