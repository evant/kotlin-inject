package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.extracting
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

@Component
abstract class InjectLambdaComponent {
    abstract val lambda: () -> String

    @Provides
    fun provideLambda(): () -> String = { "foo" }
}

@Component
abstract class InjectLambdaSetComponent {
    abstract val lambdaSet: Set<() -> String>

    @Provides
    @IntoSet
    fun lambda1(): () -> String = { "one" }

    @Provides
    @IntoSet
    fun lambda2(): () -> String = { "two" }
}

@Component
abstract class InjectLambdaMapComponent {
    abstract val mapSet: Map<String, () -> String>

    @Provides
    @IntoMap
    fun lambda1(): Pair<String, () -> String> = "1" to { "one" }

    @Provides
    @IntoMap
    fun lambda2(): Pair<String, () -> String> = "2" to { "two" }
}

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