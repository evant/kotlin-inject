package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Component
import kotlin.test.Test

@Component abstract class DefaultArgComponent(
    val required1: String,
    val optional: String = "default",
    val required2: String
)

class DefaultArgTest {
    @Test
    fun generates_a_component_with_a_create_that_skips_default_args() {
        val componentFull = DefaultArgComponent::class.create("one", "two", "three")
        val componentDefault = DefaultArgComponent::class.create("one", "three")

        assertThat(componentFull.required1).isEqualTo("one")
        assertThat(componentFull.optional).isEqualTo("two")
        assertThat(componentFull.required2).isEqualTo("three")
        assertThat(componentDefault.required1).isEqualTo("one")
        assertThat(componentDefault.optional).isEqualTo("default")
        assertThat(componentDefault.required2).isEqualTo("three")
    }
}