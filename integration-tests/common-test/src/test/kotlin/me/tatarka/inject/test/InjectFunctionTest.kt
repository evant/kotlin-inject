package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class InjectFunctionTest {

    @Test
    fun generates_a_component_that_provides_a_function() {
        val component = FunctionInjectionComponent::class.create()

        assertThat(component.bar()).isEqualTo("test")
        assertThat(component.externalFunction()).isEqualTo("external")
    }
    @Test
    fun generates_a_component_that_provides_a_function_factory() {
        val component: FunctionInjectionComponent = FunctionInjectionComponent::class.create()

        assertThat(component.barFactory()).isEqualTo("test")
        assertThat(component.externalFunctionFactory()).isEqualTo("external")
        assertThat(component.objectFunctionFactory("42").name).isEqualTo("42")
    }

    @Test
    fun generates_a_component_that_provides_a_function_with_receiver() {
        val component = ReceiverFunctionInjectionComponent::class.create()

        assertThat(with(component) { "test".receiverFun(NamedFoo("arg")) }).isEqualTo("test")
    }
}
