package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

@Inject
class AssistedBar(val foo: Foo, @Assisted val name: String)

@Component
abstract class AssistedComponent {
    abstract val barFactory: (name: String) -> AssistedBar
}

@Inject
data class AssistedBarArity2(@Assisted val arg1: String, @Assisted val arg2: String)

@Inject
data class AssistedBarArity3(@Assisted val arg1: String, @Assisted val arg2: String, @Assisted val arg3: String)

@Inject
data class AssistedBarArity4(
    @Assisted val arg1: String,
    @Assisted val arg2: String,
    @Assisted val arg3: String,
    @Assisted val arg4: String,
)

@Component
abstract class AssistedArityComponent {
    abstract val arity2: (arg1: String, arg2: String) -> AssistedBarArity2
    abstract val arity3: (arg1: String, arg2: String, arg3: String) -> AssistedBarArity3
    abstract val arity4: (arg1: String, arg2: String, arg3: String, arg4: String) -> AssistedBarArity4
}

@Inject
class NullableAssistedBar(@Assisted val foo: Foo?)

@Component
abstract class NullableAssistedComponent {
    abstract val barProvider: (Foo?) -> NullableAssistedBar
}

@Inject
data class Person(val house: House.Factory, @Assisted val name: String) {

    @Inject
    class Factory(
        val create: (String) -> Person,
    )
}

@Inject
data class House(@Assisted val bricks: Int) {
    @Inject
    class Factory(val create: (Int) -> House)
}

@Component
abstract class NestedNestedFunctionComponent {
    abstract val personFactory: Person.Factory
}

@Inject
data class OrderedAssistedBar(@Assisted val one: String, val two: Foo, @Assisted val three: String)

@Component
abstract class OrderedAssistedComponent {
    abstract val orderedBar: (String, String) -> OrderedAssistedBar
}

@Inject
data class DefaultAssistedBar(@Assisted val one: String, @Assisted val two: Int = 2)

@Component
abstract class DefaultAssistedComponent {
    abstract val withoutDefault: (String, Int) -> DefaultAssistedBar
    abstract val withDefault: (String) -> DefaultAssistedBar
}

@Inject
class UnrelatedDependency(val someString: String)

@Inject
class AssistedAndUnrelatedDep(
    @Assisted val assistedString: String,
    val unrelatedDependency: UnrelatedDependency,
)

@Component
abstract class AssistedWithOtherDependency {
    abstract val test: (String) -> AssistedAndUnrelatedDep

    @Provides
    fun string() = "provided"
}

fun interface AssistedBarFactory {
    fun create(name: String): AssistedBar
}

@Component
abstract class AssistedWithFunInterface {
    abstract val factory: AssistedBarFactory
}

class AssistedTest {

    @Test
    fun nested_arguments_do_not_cause_name_clash() {
        val component = NestedNestedFunctionComponent::class.create()
        assertThat(component.personFactory.create("Alice").name).isEqualTo("Alice")
    }

    @Test
    fun generates_a_component_that_provides_an_assisted_function() {
        val component = AssistedComponent::class.create()

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
    fun generates_a_component_that_provides_an_assisted_sam_factory() {
        val component = AssistedWithFunInterface::class.create()

        assertThat(component.factory.create(name = "name").name).isEqualTo("name")
    }
}