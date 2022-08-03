package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

abstract class AbstractParentComponent {
    @Provides
    abstract fun foo(): NamedFoo

    abstract val bar: INamedBar
}

@Component
abstract class ParentComponentImpl1 : AbstractParentComponent() {
    override fun foo(): NamedFoo = NamedFoo("parent1")

    @Provides
    fun bar2(): INamedBar = NamedBar("parent1")
}

@Component
abstract class ParentComponentImpl2 : AbstractParentComponent() {
    override fun foo(): NamedFoo = NamedFoo("parent2")

    @Provides
    fun bar2(): INamedBar = NamedBar("parent2")
}

@Component
abstract class AbstractParentChildComponent(@Component val parent: AbstractParentComponent) {
    abstract val foo: NamedFoo
    abstract val bar: INamedBar
}

@CustomScope
abstract class ScopedAbstractParentComponent

@Component
abstract class ScopedParentComponentImpl1 : ScopedAbstractParentComponent()

@Component
abstract class ScopedParentComponentImpl2 : ScopedAbstractParentComponent()

@Component
abstract class ScopedAbstractParentChildComponent(@Component val parent: ScopedAbstractParentComponent) {
    abstract val bar: CustomScopeBar
}

class AbstractComponentTest {
    @Test
    fun generates_a_component_that_provides_a_dep_from_an_abstract_parent_component() {
        val parent1 = ParentComponentImpl1::class.create()
        val parent2 = ParentComponentImpl2::class.create()
        val component1 = AbstractParentChildComponent::class.create(parent1)
        val component2 = AbstractParentChildComponent::class.create(parent2)

        assertThat(component1.foo.name).isEqualTo("parent1")
        assertThat(component1.bar.name).isEqualTo("parent1")
        assertThat(component2.foo.name).isEqualTo("parent2")
        assertThat(component2.bar.name).isEqualTo("parent2")
    }

    @Test
    fun generates_a_component_that_provides_a_dep_from_an_abstract_scoped_component() {
        val parent1 = ScopedParentComponentImpl1::class.create()
        val parent2 = ScopedParentComponentImpl2::class.create()
        val component1 = ScopedAbstractParentChildComponent::class.create(parent1)
        val component2 = ScopedAbstractParentChildComponent::class.create(parent2)

        assertThat(component1.bar).isNotNull()
        assertThat(component2.bar).isNotNull()
    }
}