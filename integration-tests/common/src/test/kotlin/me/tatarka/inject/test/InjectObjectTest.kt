package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.test.different.DifferentPackageFoo
import kotlin.test.Test

@Inject
object FooObject

@Inject
class DependsOnFooObject(val foo: FooObject)

@Component
abstract class ObjectComponent {
    abstract val injectObject: FooObject
    abstract val differentPackageObject: DifferentPackageFoo.MyObject
    abstract fun injectObject2(): FooObject
    abstract fun dependsOnFooObject2(): DependsOnFooObject
}

interface CompanionFooInterface

interface CompanionFoo {
    @Inject
    companion object : CompanionFooInterface
}

@Inject
class DependOnCompanionFoo(val foo: CompanionFooInterface)

@Component
abstract class CompanionObjectComponent {
    abstract val foo: CompanionFooInterface
    abstract val dependsOnFoo: DependOnCompanionFoo

    val CompanionFoo.Companion.bind: CompanionFooInterface
        @Provides get() = this
}

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