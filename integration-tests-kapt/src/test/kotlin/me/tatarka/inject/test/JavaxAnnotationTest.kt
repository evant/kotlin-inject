package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import javax.inject.Inject
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

class JavaxAnnotationTest {

    @Test
    fun generates_a_component_that_provides_a_dep_using_javax_annotations() {
        val component = JavaxComponent::class.create()

        assertThat(component.foo).isNotNull()
    }
}