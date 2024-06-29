package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

@Component
abstract class JavaInjectConstructorComponent {
    abstract val foo: JavaFoo

    @Provides
    fun Foo.bind(): IFoo = this

    abstract val foo2: JavaJavaXFoo
}

@Inject
class JavaBar(val foo: JavaFoo)

@Inject
class JavaJavaXBar(val foo: JavaJavaXFoo)

@Component
abstract class JavaProvidesComponent {
    abstract val bar: JavaBar

    @Provides
    fun Foo.bind(): IFoo = this

    abstract val bar2: JavaJavaXBar
}

class JavaTest {
    @Test
    fun generates_a_component_that_provides_a_class_declared_in_java() {
        val component = JavaInjectConstructorComponent::class.create()

        assertThat(component.foo).isNotNull()
        assertThat(component.foo2).isNotNull()
    }

    @Test
    fun generates_a_component_that_provides_a_class_that_depends_on_a_class_declared_in_java() {
        val component = JavaProvidesComponent::class.create()

        assertThat(component.bar.foo).isNotNull()
        assertThat(component.bar2.foo).isNotNull()
    }
}