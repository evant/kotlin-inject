package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Component
import kotlin.test.Test

@Inject class LazyFoo

@Component abstract class LazyComponent {
    abstract val foo: Lazy<LazyFoo>
}

class LazyComponentTest {

    @Test
    fun generates_a_component_that_provides_a_lazy_dep() {
        val component = LazyComponent::class.create()

        val foo = component.foo

        assertThat(foo.value).isSameAs(foo.value)
    }
}
