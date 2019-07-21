package me.tatarka.inject.sample

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import org.junit.Before
import org.junit.Test

@Inject class Foo : IFoo

@Module abstract class Module1 {
    abstract val foo: Foo
}

@Inject class Bar(val foo: Foo)

@Module abstract class Module2 {
    abstract val bar: Bar
}

interface IFoo

class ModuleTest {

    @Before
    fun setup() {
        customScopeBarConstructorCount = 0
    }

    @Test
    fun generates_a_module_that_provides_a_dep_with_no_arguments() {
        val module = Module1::class.create()

        assertThat(module.foo).isNotNull()
    }

    @Test
    fun generates_a_module_that_provides_a_dep_with_an_argument() {
        val module = Module2::class.create()

        assertThat(module.bar).isNotNull()
        assertThat(module.bar.foo).isNotNull()
    }

    @Test
    fun generates_a_module_that_constructs_different_values_based_on_the_named_qualifier() {
        val module = ConstructorAliasedModule::class.create()

        assertAll {
            assertThat(module.aliasedFoo.foo1.name).isEqualTo("1")
            assertThat(module.aliasedFoo.foo2.name).isEqualTo("2")
        }
    }
}