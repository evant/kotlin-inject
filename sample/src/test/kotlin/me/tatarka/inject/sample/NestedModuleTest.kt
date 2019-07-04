package me.tatarka.inject.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Module
import me.tatarka.inject.annotations.Provides
import org.junit.Test

@Module abstract class ParentModule {
    @Provides fun foo() = NamedFoo("parent")

    companion object
}

@Module abstract class SimpleChildModule(val parent: ParentModule) {
    abstract val foo: NamedFoo

    companion object
}


class NestedModuleTest {
    @Test
    fun generates_a_module_that_provides_a_dep_from_a_parent_module() {
        val parent = ParentModule.create()
        val module = SimpleChildModule.create(parent)

        assertThat(module.foo.name).isEqualTo("parent")
    }
}