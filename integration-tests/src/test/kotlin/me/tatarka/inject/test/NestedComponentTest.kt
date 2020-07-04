package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

@Component abstract class ParentComponent {
    @Provides
    fun foo() = NamedFoo("parent")
}

@Component abstract class SimpleChildComponent1(@Component val parent: ParentComponent) {
    abstract val foo: NamedFoo
}

@Component abstract class SimpleChildComponent2(@Component val parent: SimpleChildComponent1) {
    abstract val foo: NamedFoo
}

class NestedComponentTest {
    @Test
    fun generates_a_component_that_provides_a_dep_from_a_parent_component() {
        val parent = ParentComponent::class.create()
        val component = SimpleChildComponent1::class.create(parent)

        assertThat(component.foo.name).isEqualTo("parent")
    }

    @Test
    fun generates_a_component_that_provide_a_dep_from_2_parents_up() {
        val parent = ParentComponent::class.create()
        val child1 = SimpleChildComponent1::class.create(parent)
        val component = SimpleChildComponent2::class.create(child1)

        assertThat(component.foo.name).isEqualTo("parent")
    }
}