package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.test.different.DifferentPackageFoo
import org.junit.Test

@Inject
object FooObject

@Inject
class DependsOnFooObject(val foo: FooObject)

@Component
abstract class ObjectComponent {
    abstract val injectObject: FooObject
    abstract val differentPackageObject: DifferentPackageFoo.MyObject
    abstract fun injectObject(): FooObject
    abstract fun dependsOnFooObject(): DependsOnFooObject
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
        assertThat(component.injectObject()).isSameAs(FooObject)
    }

    @Test
    fun inject_annotated_object_can_be_provided_in_component_property() {
        val component = ObjectComponent::class.create()
        assertThat(component.injectObject).isSameAs(FooObject)
    }

    @Test
    fun inject_annotated_object_can_be_provided_to_class_constructors() {
        val component = ObjectComponent::class.create()
        assertThat(component.dependsOnFooObject().foo).isSameAs(FooObject)
    }

    @Test
    fun inject_annotated_object_can_be_provided_from_a_different_package() {
        val component = ObjectComponent::class.create()
        assertThat(component.differentPackageObject).isSameAs(DifferentPackageFoo.MyObject)
    }

    @Test
    fun binds_a_companion_object_to_an_interface() {
        val component = CompanionObjectComponent::class.create()

        assertThat(component.foo).isSameAs(CompanionFoo)
        assertThat(component.dependsOnFoo.foo).isSameAs(CompanionFoo)
    }
}