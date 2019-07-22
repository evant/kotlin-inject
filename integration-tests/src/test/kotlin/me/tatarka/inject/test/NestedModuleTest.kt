package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Module
import org.junit.Test

@Module abstract class ParentModule {
    fun foo() = NamedFoo("parent")
}

@Module abstract class SimpleChildModule(val parent: ParentModule) {
    abstract val foo: NamedFoo
}

@Module abstract class SimpleChildModule2(val parent: SimpleChildModule) {
    abstract val foo: NamedFoo
}

class NestedModuleTest {
    @Test
    fun generates_a_module_that_provides_a_dep_from_a_parent_module() {
        val parent = ParentModule::class.create()
        val module = SimpleChildModule::class.create(parent)

        assertThat(module.foo.name).isEqualTo("parent")
    }

    @Test
    fun generates_a_module_that_provide_a_dep_from_2_parents_up() {
        val parent = ParentModule::class.create()
        val child1 = SimpleChildModule::class.create(parent)
        val module = SimpleChildModule2::class.create(child1)

        assertThat(module.foo.name).isEqualTo("parent")
    }
}