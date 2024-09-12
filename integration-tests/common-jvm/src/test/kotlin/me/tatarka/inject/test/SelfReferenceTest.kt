package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import kotlin.test.Test

class SelfReferenceTest {
    @Test
    fun creates_a_component_with_a_companion_that_references_itself() {
        val component = SelfReferenceCompanionComponent

        assertThat(component.foo).isNotNull()
    }

    @Test
    fun creates_a_component_with_an_inner_class_that_references_itself() {
        val component = SelfReferenceInnerClassComponent.Instance()

        assertThat(component.foo).isNotNull()
    }
}