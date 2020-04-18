package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Module
import kotlin.test.Test

interface ModuleInterface {
    val foo: Foo
}

@Module abstract class InterfaceModule : ModuleInterface

interface GenericModuleInterface<T> {
    val foo: T
}

@Module abstract class GenericInterfaceModule : GenericModuleInterface<Foo>

class InheritanceTest {
    @Test fun generates_a_module_that_provides_a_dep_defined_in_an_implemented_interface() {
        val module = InterfaceModule::class.create()

        assertThat(module.foo).isNotNull()
    }

    @Test fun generates_a_module_that_provides_a_dep_defined_in_a_generic_implemented_interface() {
        val module = GenericInterfaceModule::class.create()

        assertThat(module.foo).hasClass(Foo::class)
    }
}