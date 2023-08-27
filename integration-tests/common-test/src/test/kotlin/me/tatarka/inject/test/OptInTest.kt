package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class OptInTest {

    @Test
    @OptIn(FileOptIn::class, ClassOptIn::class, ClassOptIn2::class)
    fun generates_a_component_that_uses_an_opt_in_api() {
        val component = OptInFileComponent::class.create(OptInBar())

        assertThat(component.foo).isInstanceOf(OptInFoo::class)
    }
}