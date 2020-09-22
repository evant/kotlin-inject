package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Scope
import kotlin.test.Test

@Scope
annotation class JavaxScope

@JavaxScope
class JavaxFoo @Inject constructor()

@JavaxScope
@Component
abstract class JavaxComponent {
    abstract val foo: JavaxFoo
}

@Component
abstract class NamedComponent {

    @get:Named("one")
    abstract val one: String

    @get:Named("two")
    abstract val two: String

    val provide1: String
        @Provides @Named("one") get() = "one"

    val provide2: String
        @Provides @Named("two") get() = "two"
}

class JavaxAnnotationTest {

    @Test
    fun generates_a_component_that_provides_a_dep_using_javax_annotations() {
        val component = JavaxComponent::class.create()

        assertThat(component.foo).isNotNull()
    }

    @Test
    fun generates_a_component_that_supports_the_named_qualifier() {
        val component = NamedComponent::class.create()

        assertThat(component.one).isEqualTo("one")
        assertThat(component.two).isEqualTo("two")
    }
}