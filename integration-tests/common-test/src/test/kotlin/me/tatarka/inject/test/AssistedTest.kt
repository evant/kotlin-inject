package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import kotlin.test.Test

class AssistedTest {

    @Test
    fun nested_arguments_do_not_cause_name_clash() {
        val component = NestedNestedFunctionComponent::class.create()
        assertThat(component.personFactory.create("Alice").name).isEqualTo("Alice")
    }

    @Test
    fun generates_a_component_that_provides_an_assisted_function() {
        val component:AssistedComponent = AssistedComponent::class.create()

        assertThat(component.barFactory("name").name).isEqualTo("name")
    }

    @Test
    fun generates_a_component_that_provides_assisted_functions_of_various_arities() {
        val component = AssistedArityComponent::class.create()

        assertThat(component.arity2("1", "2")).isEqualTo(AssistedBarArity2("1", "2"))
        assertThat(component.arity3("1", "2", "3")).isEqualTo(AssistedBarArity3("1", "2", "3"))
        assertThat(component.arity4("1", "2", "3", "4")).isEqualTo(AssistedBarArity4("1", "2", "3", "4"))
    }

    @Test
    fun generates_a_component_that_provides_a_nullable_function_that_returns_a_dep() {
        val component = NullableAssistedComponent::class.create()

        val foo = Foo()
        assertThat(component.barProvider(foo).foo).isSameInstanceAs(foo)
    }

    @Test
    fun generates_a_component_that_provides_multiple_assisted_params_in_order() {
        val component = OrderedAssistedComponent::class.create()

        assertThat(component.orderedBar("one", "three")).all {
            prop(OrderedAssistedBar::one).isEqualTo("one")
            prop(OrderedAssistedBar::three).isEqualTo("three")
        }
    }

    @Test
    fun generates_a_component_that_allows_omitting_an_assisted_param_with_a_default_value() {
        val component = DefaultAssistedComponent::class.create()

        assertThat(component.withoutDefault("one", 2)).isEqualTo(DefaultAssistedBar("one", 2))
        assertThat(component.withDefault("one")).isEqualTo(DefaultAssistedBar("one", 2))
    }

    @Test
    fun generates_a_component_that_separates_assisted_and_provided_values() {
        val component = AssistedWithOtherDependency::class.create()

        assertThat(component.test("assisted")).all {
            prop(AssistedAndUnrelatedDep::assistedString).isEqualTo("assisted")
            prop(AssistedAndUnrelatedDep::unrelatedDependency).prop(UnrelatedDependency::someString)
                .isEqualTo("provided")
        }
    }

    @Test
    fun generates_a_component_with_assisted_factory() {
        val component: AssistedFactoryComponent = AssistedFactoryComponent::class.create()
        val expectedName = "asd"
        val expectedNum = 42
        assertThat(component.barFactory.build(expectedNum, expectedName)).all {
            prop(FactoryAssistedBar::name).isEqualTo(expectedName)
            prop(FactoryAssistedBar::num).isEqualTo(expectedNum)
        }
    }

    @Test
    fun generates_a_component_that_injects_assisted_factory_in_constructor() {
        val component: AssistedFactoryComponent = AssistedFactoryComponent::class.create()
        assertThat(component.somethingDependant.factory.build(42, "asd")).isNotNull()
    }
}
