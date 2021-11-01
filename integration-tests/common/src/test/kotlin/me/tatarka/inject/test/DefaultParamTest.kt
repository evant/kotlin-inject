package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

@Inject
class FooWithDefault(override val name: String = "default") : IFooWithDefault

interface IFooWithDefault {
    val name: String
}

typealias fooWithDefaultFun = () -> IFooWithDefault

@Inject
fun fooWithDefaultFun(name: String = "default"): IFooWithDefault {
    return FooWithDefault(name)
}

@Component
abstract class UseDefaultComponent {
    abstract val foo: FooWithDefault
    abstract val iFoo: IFooWithDefault
    abstract val fooFun: fooWithDefaultFun

    @Provides
    fun iFoo(name: String = "default"): IFooWithDefault = FooWithDefault(name)
}

@Component
abstract class OverrideDefaultComponent {
    abstract val foo: FooWithDefault
    abstract val iFoo: IFooWithDefault
    abstract val fooFun: fooWithDefaultFun

    val name: String
        @Provides get() = "override"

    @Provides
    fun iFoo(name: String = "default"): IFooWithDefault = FooWithDefault(name)
}

class DefaultParamTest {
    @Test
    fun generates_a_component_that_uses_a_default_value_for_a_param() {
        val component = UseDefaultComponent::class.create()

        assertThat(component.foo.name).isEqualTo("default")
        assertThat(component.iFoo.name).isEqualTo("default")
        assertThat(component.fooFun().name).isEqualTo("default")
    }

    @Test
    fun generates_a_component_that_overrides_a_default_value_for_a_param() {
        val component = OverrideDefaultComponent::class.create()

        assertThat(component.foo.name).isEqualTo("override")
        assertThat(component.iFoo.name).isEqualTo("override")
        assertThat(component.fooFun().name).isEqualTo("override")
    }
}