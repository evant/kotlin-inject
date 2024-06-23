package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import kotlin.test.Test

class ComplexCycleTest {
    @Test
    fun generates_a_component_with_complex_cycle() {
        val component: ComplexCycleComponent = ComplexCycleComponent::class.create()

        assertThat(component.lvl1).isNotNull()
    }
}