package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isInstanceOf
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

@Component abstract class SuspendFunctionComponent {
    abstract val foo: () -> IFoo

    abstract val suspendFoo: suspend () -> IFoo

    val suspendFooFunction: suspend () -> IFoo
        @Provides get() = { Foo() }

    val fooFunction: () -> IFoo
        @Provides get() = { Foo() }
}

@Component abstract class SuspendProviderComponent {
    abstract suspend fun suspendFoo(): IFoo

    @Provides suspend fun suspendProvides(): IFoo = Foo()
}

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