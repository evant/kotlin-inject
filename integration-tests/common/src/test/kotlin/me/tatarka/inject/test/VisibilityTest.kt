package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Component
import org.junit.Test
import kotlin.reflect.KVisibility
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties

@Component
abstract class VisibilityTestComponent {
    abstract val myPublicProperty: Foo
    internal abstract val myInternalProperty: InternalBarConstructor
    protected abstract val myProtectedProperty: Foo

    abstract fun myPublicFunction(): InternalBarConstructor
    internal abstract fun myInternalFunction(): InternalFoo
    protected abstract fun myProtectedFunction(): Foo
}

@Component internal interface InternalVisibilityTestComponent

class VisibilityTest {

    @Test
    fun public_component_generates_public_properties() {
        assertThat(property("myPublicProperty").visibility).isEqualTo(KVisibility.PUBLIC)
    }

    @Test
    fun public_component_generates_internal_properties() {
        assertThat(property("myInternalProperty").visibility).isEqualTo(KVisibility.INTERNAL)
    }

    @Test
    fun public_component_generates_protected_properties() {
        assertThat(property("myProtectedProperty").visibility).isEqualTo(KVisibility.PROTECTED)
    }

    @Test
    fun public_component_generates_public_function() {
        assertThat(function("myPublicFunction").visibility).isEqualTo(KVisibility.PUBLIC)
    }

    @Test
    fun public_component_generates_internal_function() {
        assertThat(function("myInternalFunction").visibility).isEqualTo(KVisibility.INTERNAL)
    }

    @Test
    fun public_component_generates_protected_function() {
        assertThat(function("myProtectedFunction").visibility).isEqualTo(KVisibility.PROTECTED)
    }

    @Test
    fun internal_component_generates_internal_create() {
        assertThat(InternalVisibilityTestComponent::class.create()::class.visibility).isEqualTo(KVisibility.INTERNAL)
    }

    private fun property(name: String) = InjectVisibilityTestComponent::class.memberProperties.first { it.name == name }

    private fun function(name: String) = InjectVisibilityTestComponent::class.functions.first { it.name == name }
}