package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

@Component abstract class ParentComponent {
    abstract val parentNamedFoo: NamedFoo

    abstract val parentBaz: IBaz

    @Provides
    fun foo() = NamedFoo("parent")

    val Foo.binds: IFoo
        @Provides get() = this

    protected val Baz.bind: IBaz
        @Provides get() = this
}

@Component abstract class SimpleChildComponent1(@Component val parent: ParentComponent) {
    abstract val namedFoo: NamedFoo

    val BarImpl.binds: IBar
        @Provides get() = this

    abstract val foo: IFoo

    abstract val baz: IBaz
}

@Component abstract class SimpleChildComponent2(@Component val parent: SimpleChildComponent1) {
    abstract val namedFoo: NamedFoo

    abstract val foo: IFoo

    abstract val bar: IBar
}

class NestedComponentTest {
    @Test
    fun generates_a_component_that_provides_a_dep_from_a_parent_component() {
        val parent = ParentComponent::class.create()
        val component = SimpleChildComponent1::class.create(parent)

        assertThat(component.parent.parentNamedFoo.name).isEqualTo("parent")
        assertThat(component.namedFoo.name).isEqualTo("parent")
        assertThat(component.foo).isNotNull()
        assertThat(component.baz).isNotNull()
    }

    @Test
    fun generates_a_component_that_provide_a_dep_from_2_parents_up() {
        val parent = ParentComponent::class.create()
        val child1 = SimpleChildComponent1::class.create(parent)
        val component = SimpleChildComponent2::class.create(child1)

        assertThat(component.parent.parent.parentNamedFoo.name).isEqualTo("parent")
        assertThat(component.namedFoo.name).isEqualTo("parent")
        assertThat(component.foo).isNotNull()
        assertThat(component.bar).isNotNull()
    }
}