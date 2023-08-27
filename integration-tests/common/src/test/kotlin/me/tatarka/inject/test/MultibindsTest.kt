package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.containsOnly
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

data class FooValue(val name: String)

@Component
abstract class SetComponent {
    abstract val items: Set<FooValue>

    abstract val funItems: Set<() -> FooValue>

    abstract val lazyItems: Set<Lazy<FooValue>>

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

typealias Factory<T> = () -> T

@Component
abstract class TypeAliasIntoMapDependencyComponent {
    abstract val items: Map<String, () -> Any?>

    @Provides
    @IntoMap
    fun foo(f: Factory<Foo>): Pair<String, () -> Any?> = "test" to f
}

@Component
abstract class AssistedSetComponent {
    abstract val items: Set<(String) -> FooValue>

    @Provides
    @IntoSet
    fun fooValue1(@Assisted arg: String): FooValue = FooValue("${arg}1")

    @Provides
    @IntoSet
    fun fooValue2(@Assisted arg: String): FooValue = FooValue("${arg}2")
}

typealias MyString = String

@Component
abstract class TypeAliasSetComponent {
    abstract val stringItems: Set<String>

    abstract val myStringItems: Set<MyString>

    @Provides
    @IntoSet
    fun stringValue1(): String = "string"

    @Provides
    @IntoSet
    fun stringValue2(): MyString = "myString"
}

@Component
abstract class TypeAliasKeyMapComponent {
    abstract val stringMap: Map<String, String>

    abstract val myStringMap: Map<MyString, String>

    @Provides
    @IntoMap
    fun stringEntry1(): Pair<String, String> = "1" to "string"

    @Provides
    @IntoMap
    fun myStringEntry1(): Pair<MyString, String> = "1" to "myString"
}

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
}