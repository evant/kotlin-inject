package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Component
import kotlin.test.Test

@Inject class FunctionFoo

@Component abstract class FunctionComponent {
    abstract val fooProvider: () -> FunctionFoo
}

@Inject class PartialFunctionBar(val foo: FunctionFoo, val name: String)

@Component abstract class PartialFunctionComponent {
    abstract val barFactory: (name: String) -> PartialFunctionBar
}

@Inject data class ArityFunctionBar2(val arg1: String, val arg2: String)

@Inject data class ArityFunctionBar3(val arg1: String, val arg2: String, val arg3: String)

@Inject data class ArityFunctionBar4(val arg1: String, val arg2: String, val arg3: String, val arg4: String)

@Component abstract class FunctionArityComponent {
    abstract val arity2: (arg1: String, arg2: String) -> ArityFunctionBar2
    abstract val arity3: (arg1: String, arg2: String, arg3: String) -> ArityFunctionBar3
    abstract val arity4: (arg1: String, arg2: String, arg3: String, arg4: String) -> ArityFunctionBar4
}

@Inject class NullableFunctionBar(val foo: FunctionFoo?)

@Component abstract class NullableFunctionComponent {
    abstract val barProvider: (FunctionFoo?) -> NullableFunctionBar
}

class FunctionTest {

    @Test fun generates_a_component_that_provides_a_function_that_returns_a_dep() {
        val component = FunctionComponent::class.create()

        assertThat(component.fooProvider()).isNotNull()
    }

    @Test fun generates_a_component_that_provides_a_function_that_partially_creates_a_dep() {
        val component = PartialFunctionComponent::class.create()

        assertThat(component.barFactory("name").name).isEqualTo("name")
    }

    @Test fun generates_a_component_that_provides_functions_of_various_arities() {
        val component = FunctionArityComponent::class.create()

        assertThat(component.arity2("1", "2")).isEqualTo(ArityFunctionBar2("1", "2"))
        assertThat(component.arity3("1", "2", "3")).isEqualTo(ArityFunctionBar3("1", "2", "3"))
        assertThat(component.arity4("1", "2", "3", "4")).isEqualTo(ArityFunctionBar4("1", "2", "3", "4"))
    }

    @Test fun generates_a_component_that_provides_a_nullable_function_that_returns_a_dep() {
        val component = NullableFunctionComponent::class.create()

        val foo = FunctionFoo()
        assertThat(component.barProvider(foo).foo).isSameAs(foo)
    }
}