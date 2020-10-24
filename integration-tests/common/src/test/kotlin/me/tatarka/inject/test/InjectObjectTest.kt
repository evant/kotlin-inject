package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.test.different.DifferentPackageFoo
import org.junit.Test

@Inject object MyInjectObject

@Inject class DependsOnMyInjectObject(val myInjectObject: MyInjectObject)

@Component interface MyComponent {
    val injectObject: MyInjectObject
    val differentPackageObject: DifferentPackageFoo.MyObject
    fun injectObject(): MyInjectObject
    fun dependsOnMyInjectObject(): DependsOnMyInjectObject
}

class InjectObjectTest {
    @Test
    fun inject_annotated_object_can_be_provided_in_component_function() {
        assertThat(InjectMyComponent().injectObject()).isEqualTo(MyInjectObject)
    }

    @Test
    fun inject_annotated_object_can_be_provided_in_component_property() {
        assertThat(InjectMyComponent().injectObject).isEqualTo(MyInjectObject)
    }

    @Test
    fun inject_annotated_object_can_be_provided_to_class_constructors() {
        assertThat(InjectMyComponent().dependsOnMyInjectObject().myInjectObject).isEqualTo(MyInjectObject)
    }

    @Test
    fun inject_annotated_object_can_be_provided_from_a_different_package() {
        assertThat(InjectMyComponent().differentPackageObject).isEqualTo(DifferentPackageFoo.MyObject)
    }
}