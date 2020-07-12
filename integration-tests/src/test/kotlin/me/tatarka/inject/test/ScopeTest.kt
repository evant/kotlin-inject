package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import kotlin.test.BeforeTest
import kotlin.test.Test

@CustomScope @Component abstract class CustomScopeConstructorComponent {
    abstract val bar: CustomScopeBar
}

@CustomScope @Component abstract class CustomScopeProvidesComponent {

    abstract val foo: IFoo

    @Provides
    @CustomScope
    val Foo.binds: IFoo
        get() = this
}


@Component abstract class ParentScopedComponent(@Component val parent: CustomScopeConstructorComponent) {
    abstract val bar: CustomScopeBar
}

@Component abstract class ParentParentScopedComponent(@Component val parent: ParentScopedComponent) {
    abstract val bar: CustomScopeBar
}

@Component abstract class NonCustomScopeParentComponent

@CustomScope @Component abstract class CustomScopeChildComponent(@Component val parent: NonCustomScopeParentComponent) {
    abstract val bar: CustomScopeBar
}

class ScopeTest {
    @BeforeTest
    fun setup() {
        fooConstructorCount = 0
        customScopeBarConstructorCount = 0
    }

    @Test
    fun generates_a_component_where_a_custom_scope_constructor_is_only_called_once() {
        val component = CustomScopeConstructorComponent::class.create()
        component.bar
        component.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_component_where_a_custom_scope_provides_is_only_called_once() {
        val component = CustomScopeProvidesComponent::class.create()
        component.foo
        component.foo

        assertThat(fooConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_component_where_a_singleton_constructor_is_instantiated_in_the_parent_component() {
        val parent = CustomScopeConstructorComponent::class.create()
        val component1 = ParentScopedComponent::class.create(parent)
        component1.bar
        component1.bar
        val component2 = ParentScopedComponent::class.create(parent)
        component1.bar
        component2.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_component_where_a_singleton_constructor_is_instantiated_in_the_parent2_component() {
        val parent = CustomScopeConstructorComponent::class.create()
        val child = ParentScopedComponent::class.create(parent)
        val component1 = ParentParentScopedComponent::class.create(child)
        component1.bar
        component1.bar
        val component2 = ParentParentScopedComponent::class.create(child)
        component1.bar
        component2.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_scoped_component_with_an_unscoped_parent_component() {
        val parent = NonCustomScopeParentComponent::class.create()
        val child = CustomScopeChildComponent::class.create(parent)

        assertThat(child.bar).isNotNull()
    }
}