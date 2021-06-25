package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import kotlin.reflect.full.declaredMembers
import kotlin.test.Test

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FunctionAnnotation

typealias annotatedF = @FunctionAnnotation () -> String

@Inject
@FunctionAnnotation
@Suppress("FunctionOnlyReturningConstant")
fun annotatedF(): String {
    return "test"
}

@Component
abstract class FunctionAnnotationInjectComponent {
    abstract val baz: annotatedF
}

class InjectFunctionTestJvm {

    @Test
    fun generates_a_component_that_provides_a_function_with_an_annotation() {
        val component = FunctionAnnotationInjectComponent::class.create()
        val member = component::class.declaredMembers.first { it.name == "baz" }
        val annotations = member.returnType.annotations

        assertThat(annotations).extracting { it.annotationClass }
            .containsExactly(FunctionAnnotation::class)
    }
}