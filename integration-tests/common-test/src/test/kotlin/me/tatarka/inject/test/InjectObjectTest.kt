package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import me.tatarka.inject.test.different.DifferentPackageFoo
import kotlin.test.Test

class InjectObjectTest {
    @Test
    fun inject_annotated_object_can_be_provided_in_component_function() {
        val component = ObjectComponent::class.create()
        assertThat(component.injectObject2()).isSameInstanceAs(FooObject)
    }

    @Test
    fun inject_annotated_object_can_be_provided_in_component_property() {
        val component = ObjectComponent::class.create()
        assertThat(component.injectObject).isSameInstanceAs(FooObject)
    }

    @Test
    fun inject_annotated_object_can_be_provided_to_class_constructors() {
        val component = ObjectComponent::class.create()
        assertThat(component.dependsOnFooObject2().foo).isSameInstanceAs(FooObject)
    }

    @Test
    fun inject_annotated_object_can_be_provided_from_a_different_package() {
        val component = ObjectComponent::class.create()
        assertThat(component.differentPackageObject).isSameInstanceAs(DifferentPackageFoo.MyObject)
    }

    @Test
    fun binds_a_companion_object_to_an_interface() {
        val component = CompanionObjectComponent::class.create()

        assertThat(component.foo).isSameInstanceAs(CompanionFoo)
        assertThat(component.dependsOnFoo.foo).isSameInstanceAs(CompanionFoo)
    }
}
