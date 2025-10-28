package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SuspendTest {
    @Test
    fun generates_a_component_that_provides_a_suspend_function() = runTest {
        val component = SuspendFunctionComponent::class.create()

        assertThat(component.suspendFoo()).isInstanceOf(Foo::class)
    }

    @Test
    fun generates_a_component_that_has_a_suspending_provides() = runTest {
        val component = SuspendProviderComponent::class.create()

        assertThat(component.suspendFoo()).isInstanceOf(Foo::class)
    }
}
