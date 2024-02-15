package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.extracting
import assertk.assertions.isEqualTo
import kotlin.test.Test

class InjectLambdaTest {

    @Test
    fun generates_a_component_that_provides_a_lambda() {
        val component = InjectLambdaComponent::class.create()

        assertThat(component.lambda.invoke()).isEqualTo("foo")
    }

    @Test
    fun generates_a_component_that_provides_a_set_of_lambdas() {
        val component = InjectLambdaSetComponent::class.create()

        assertThat(component.lambdaSet)
            .extracting { it.invoke() }
            .containsOnly("one", "two")
    }

    @Test
    fun generates_a_component_that_provides_a_map_of_lambdas() {
        val component = InjectLambdaMapComponent::class.create()

        assertThat(component.mapSet.mapValues { (_, value) -> value() })
            .containsOnly(
                "1" to "one",
                "2" to "two"
            )
    }
}
