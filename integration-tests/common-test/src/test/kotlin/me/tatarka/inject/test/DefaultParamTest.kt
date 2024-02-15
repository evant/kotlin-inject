package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DefaultParamTest {
    @Test
    fun generates_a_component_that_uses_a_default_value_for_a_param() {
        val component = UseDefaultComponent::class.create()

        assertThat(component.foo.name).isEqualTo("default")
        assertThat(component.foo.fn()).isEqualTo("default")
        assertThat(component.foo.lazy.value).isEqualTo("default")
        assertThat(component.iFoo.name).isEqualTo("default")
        assertThat(component.iFoo.fn()).isEqualTo("default")
        assertThat(component.iFoo.lazy.value).isEqualTo("default")
        assertThat(component.fooFun().name).isEqualTo("default")
        assertThat(component.fooFun().fn()).isEqualTo("default")
        assertThat(component.fooFun().lazy.value).isEqualTo("default")
    }

    @Test
    fun generates_a_component_that_overrides_a_default_value_for_a_param() {
        val component = OverrideDefaultComponent::class.create()

        assertThat(component.foo.name).isEqualTo("override")
        assertThat(component.foo.fn()).isEqualTo("override")
        assertThat(component.foo.lazy.value).isEqualTo("override")
        assertThat(component.iFoo.name).isEqualTo("override")
        assertThat(component.iFoo.fn()).isEqualTo("override")
        assertThat(component.iFoo.lazy.value).isEqualTo("override")
        assertThat(component.fooFun().name).isEqualTo("override")
        assertThat(component.fooFun().fn()).isEqualTo("override")
        assertThat(component.fooFun().lazy.value).isEqualTo("override")
    }
}
