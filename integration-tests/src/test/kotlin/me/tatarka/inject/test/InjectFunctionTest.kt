package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import org.junit.Test

@Component abstract class FunctionInjectionComponent {
    abstract val bar: bar
}

typealias foo = (String) -> String

@Inject
fun foo(dep: Foo, arg: String): String = arg

typealias bar = () -> String

@Inject
fun bar(foo: foo): String = foo("test")

class InjectFunctionTest {

    @Test
    fun generates_a_component_that_provides_a_function() {
        val component = FunctionInjectionComponent::class.create()

        assertThat(component.bar()).isEqualTo("test")
    }

}


