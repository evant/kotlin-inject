package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import kotlin.test.Test

class InjectConstructorTest {

    @Test
    fun class_with_inject_annotated_primary_constructor_can_be_provided() {
        val component = InjectCtorComponent::class.create()

        assertThat(component.primaryInject).isNotNull()
    }

    @Test
    fun class_with_inject_annotated_secondary_constructor_can_be_provided() {
        val component = InjectCtorComponent::class.create()

        assertThat(component.secondaryInject).isNotNull()
    }
}
