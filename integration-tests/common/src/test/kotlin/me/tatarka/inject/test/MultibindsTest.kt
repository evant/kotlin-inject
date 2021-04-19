package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.containsOnly
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

data class FooValue(val name: String)

@Component
abstract class SetComponent {
    abstract val items: Set<FooValue>

    @Provides
    @IntoSet
    fun fooValue1() = FooValue("1")

    val fooValue2
        @Provides @IntoSet get() = FooValue("2")
}

@Component
abstract class DynamicKeyComponent {

    abstract val items: Map<String, FooValue>

    @Provides
    @IntoMap
    fun fooValue1() = "1" to FooValue("1")

    val fooValue2
        @Provides @IntoMap get() = "2" to FooValue("2")
}

@Component
abstract class ParentSetComponent {
    val Foo.bind: IFoo
        @Provides @IntoSet get() = this

    val Bar.bind: IFoo
        @Provides @IntoSet get() = this

    // Should not be in the set
    val Baz.bind: IFoo
        @Provides get() = this
}

@Component
abstract class ChildSetComponent(@Component val parent: ParentSetComponent) {
    val Foo3.bind: IFoo
        @Provides @IntoSet get() = this

    abstract val items: Set<IFoo>
}

typealias Entry = Pair<String, FooValue>

@Component
abstract class TypeAliasMapComponent {
    abstract val items: Map<String, FooValue>

    val fooValue1: Entry
        @Provides @IntoMap get() = "1" to FooValue("1")

    val fooValue2: Entry
        @Provides @IntoMap get() = "2" to FooValue("2")
}

class MultibindsTest {

    @Test
    fun generates_a_component_that_provides_multiple_items_into_a_set() {
        val component = SetComponent::class.create()

        assertThat(component.items).containsOnly(FooValue("1"), FooValue("2"))
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
}