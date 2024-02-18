package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import me.tatarka.inject.test.different.DifferentPackageScopedComponent
import me.tatarka.inject.test.different.create
import me.tatarka.inject.test.module.ExternalChildComponent
import me.tatarka.inject.test.module.create
import kotlin.test.BeforeTest
import kotlin.test.Test

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

    @Test
    fun generates_a_scoped_component_with_scoped_deps() {
        val component = DependentCustomScopeComponent::class.create()

        assertThat(component.bar).isSameInstanceAs(component.foo.bar)
    }

    @Test
    fun generates_a_component_with_a_parent_scoped_component_in_a_different_package() {
        val component = DifferentPackageChildComponent::class.create(DifferentPackageScopedComponent::class.create())

        assertThat(component.foo).isSameInstanceAs(component.foo)
    }

    @Test
    fun generates_a_component_that_reuses_the_same_scoped_dependency() {
        val component = MultipleUseScopedComponent::class.create()

        assertThat(component.bar1.bar).isSameInstanceAs(component.bar2.bar)
    }

    @Test
    fun generates_a_component_with_unique_keys_for_scoped_access() = runTest {
        val component = TypeAccessComponent::class.create()

        assertThat(component.string).isEqualTo("string")
        assertThat(component.`class`).isEqualTo(Foo())
        assertThat(component.parameterized).isEqualTo(GenericFoo("generic"))
        assertThat(component.typeAlias1).isEqualTo(NamedFoo("one"))
        assertThat(component.typeAlias2).isEqualTo(NamedFoo("two"))
        assertThat(component.lambda("test")).isEqualTo("test lambda")
        assertThat(component.suspendLambda("test")).isEqualTo("test suspend lambda")
        assertThat(component.receiverLambda(1)).isEqualTo("1 receiver lambda")
        assertThat(component.suspendReceiverLambda(1)).isEqualTo("1 suspend receiver lambda")
    }

    @Test
    fun generates_a_component_with_nested_scoped_parent_in_another_module() {
        val component = NestedExternalScopedComponent::class.create(ExternalChildComponent::class.create())

        assertThat(component.foo).isSameInstanceAs(component.foo)
    }

    @Test
    fun generates_a_component_when_multiple_scoped_provides_funs_have_the_same_return_type_with_different_type_args() {
        val component = MultipleSameTypedScopedProvidesComponent::class.create()

        assertThat(component.foo).isSameInstanceAs(component.foo)
    }

    @Test
    fun generates_a_component_that_injects_a_parent_scoped_dep_into_a_child_scoped_dep() {
        val component = ParentChildScopesChildComponent::class.create(ParentChildScopesParentComponent::class.create())

        assertThat(component.bar).isNotNull()
    }

    @Test
    fun generates_a_component_that_applies_the_same_scope_to_itself_and_its_parent_interface() {
        val component = DuplicateScopeComponent::class.create()

        assertThat(component.foo).isNotNull()
    }
}
