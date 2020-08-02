package me.tatarka.inject.test

import assertk.assertThat
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import assertk.assertions.containsOnly
import kotlin.test.Test

data class FooValue(val name: String)

@Component abstract class SetComponent {
    abstract val items: Set<FooValue>

    @Provides @IntoSet
    fun fooValue1() = FooValue("1")

    @Provides @IntoSet
    val fooValue2
        get() = FooValue("2")
}

@Component abstract class DynamicKeyComponent {

    abstract val items: Map<String, FooValue>

    @Provides @IntoMap
    fun fooValue1() = "1" to FooValue("1")

    @Provides @IntoMap
    val fooValue2
        get() = "2" to FooValue("2")
}

@Component abstract class ParentSetComponent {
    @Provides @IntoSet
    val Foo.bind: IFoo
        get() = this

    @Provides @IntoSet
    val Bar.bind: IFoo
        get() = this
}

@Component abstract class ChildSetComponent(@Component val parent: ParentSetComponent) {
    abstract val items: Set<IFoo>
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
            Bar(Foo())
        )
    }
}