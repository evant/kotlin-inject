package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.test.Test

data class FooClass<T>(val fooClass: Any?)

@Component abstract class ReifiedProvidesComponent {
    abstract val foo: FooClass<Foo>

    abstract val bar: FooClass<Bar>

    @Provides
    inline fun <reified T: IFoo> providesFoo(): FooClass<T> = FooClass(T::class)
}

class ReflectFoo: IFoo

class ReflectBar : IFoo

@Component abstract class ReflectProvidesComponent {
    abstract val foo: ReflectFoo

    abstract val bar: ReflectBar

    @Provides
    inline fun <reified T: IFoo> providesFoo(): T = T::class.createInstance()
}

class ReifiedProvidesTest {

    @Test
    fun generates_a_component_provides_a_class_from_a_bounded_reified_generic() {
        val component = ReifiedProvidesComponent::class.create()

        assertThat(component.foo.fooClass).isEqualTo(Foo::class)
        assertThat(component.bar.fooClass).isEqualTo(Bar::class)
    }

    @Test
    fun generates_a_component_provides_an_instance_from_a_bounded_reified_generic() {
        val component = ReflectProvidesComponent::class.create()

        assertThat(component.foo).isNotNull()
        assertThat(component.bar).isNotNull()
    }
}