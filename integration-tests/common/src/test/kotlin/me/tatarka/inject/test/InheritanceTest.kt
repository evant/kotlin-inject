package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

interface ComponentInterface {
    val foo: Foo
    val bar: Bar
}

@Component
abstract class InterfaceComponentWithIdenticalProvides(
    @get:Provides
    override val foo: Foo,
    override val bar: Bar,
) : ComponentInterface

@Component
abstract class InterfaceComponent : ComponentInterface

interface GenericComponentInterface<T> {
    val genericFoo: T
}

@Component
abstract class GenericInterfaceComponent : GenericComponentInterface<Foo>

interface ProvidesComponentInterface {
    val iFoo: IFoo
        @Provides get() = Foo()
}

@Component
abstract class ProvidesInterfaceComponent : ProvidesComponentInterface {
    abstract val foo: IFoo
}

interface ProvidesIndirectInterface : ProvidesComponentInterface

@Component
abstract class ProvidesIndirectComponent : ProvidesIndirectInterface {
    abstract val foo: IFoo
}

interface ProvidesScopedComponentInterface {
    val iFoo: IFoo
        @CustomScope
        @Provides get() = Foo()
}

@Component
@CustomScope
abstract class ProvidesScopedInterfaceComponent : ProvidesScopedComponentInterface {
    abstract val foo: IFoo
}

class InheritanceTest {
    @Test
    fun generates_a_component_that_provides_a_dep_defined_in_an_implemented_interface() {
        val component = InterfaceComponent::class.create()

        assertThat(component.foo).isNotNull()
    }

    @Test
    fun generates_a_component_that_provides_a_dep_defined_in_an_indirect_interface() {
        val component = ProvidesIndirectComponent::class.create()

        assertThat(component.foo).isNotNull()
    }

    @Test
    fun generates_a_component_that_ignores_abstract_superclass_methods_that_are_overridden_by_the_component() {
        val foo = Foo()
        val bar = Bar(foo)
        val component = InterfaceComponentWithIdenticalProvides::class.create(foo, bar)

        assertThat(component.foo).isSameAs(foo)
        assertThat(component.bar).isSameAs(bar)
    }

    @Test
    fun generates_a_component_that_provides_a_dep_defined_in_a_generic_implemented_interface() {
        val component = GenericInterfaceComponent::class.create()

        assertThat(component.genericFoo).hasClass(Foo::class)
    }

    @Test
    fun generates_a_component_that_provides_a_scoped_dep_defined_in_an_implemented_interface() {
        val component = ProvidesScopedInterfaceComponent::class.create()

        assertThat(component.foo).isSameAs(component.foo)
    }
}