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

@Component abstract class SimpleChildModule(@Component val parent: ParentComponent) {
    abstract val foo: NamedFoo
}

@Component abstract class SimpleChildModule2(@Component val parent: SimpleChildModule) {
    abstract val foo: NamedFoo
}

class NestedComponentTest {
    @Test
    fun generates_a_component_that_provides_a_dep_from_a_parent_component() {
        val parent = ParentComponent::class.create()
        val component = SimpleChildModule::class.create(parent)

        assertThat(component.foo.name).isEqualTo("parent")
    }

    @Test
    fun generates_a_component_that_provide_a_dep_from_2_parents_up() {
        val parent = ParentComponent::class.create()
        val child1 = SimpleChildModule::class.create(parent)
        val component = SimpleChildModule2::class.create(child1)

        assertThat(component.foo.name).isEqualTo("parent")
    }
}