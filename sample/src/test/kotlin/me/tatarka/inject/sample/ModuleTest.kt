package me.tatarka.inject.sample

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.*
import me.tatarka.inject.annotations.*
import org.junit.Before
import org.junit.Test

@Inject class Foo : IFoo

@Module abstract class Module1 {
    abstract val foo: Foo

    companion object
}

@Inject class Bar(val foo: Foo)

@Module abstract class Module2 {
    abstract val bar: Bar

    companion object
}

interface IFoo

class NamedFoo(val name: String)

@Module abstract class Module7 {
    @get:Named("1")
    abstract val foo1: NamedFoo

    @get:Named("2")
    abstract val foo2: NamedFoo

    @Named("1") fun foo1() = NamedFoo("1")
    @Named("2") fun foo2() = NamedFoo("2")

    companion object
}

@Inject class QualifiedFoo(@Named("1") val foo1: NamedFoo, @Named("2") val foo2: NamedFoo)

@Module abstract class Module9 {
    abstract val qualifiedFoo: QualifiedFoo

    @Named("1") fun foo1() = NamedFoo("1")
    @Named("2") fun foo2() = NamedFoo("2")

    companion object
}

class ModuleTest {

    @Before
    fun setup() {
        customScopeBarConstructorCount = 0
    }

    @Test
    fun generates_a_module_that_provides_a_dep_with_no_arguments() {
        val module = Module1.create()

        assertThat(module.foo).isNotNull()
    }

    @Test
    fun generates_a_module_that_provides_a_dep_with_an_argument() {
        val module = Module2.create()

        assertThat(module.bar).isNotNull()
        assertThat(module.bar.foo).isNotNull()
    }

    @Test
    fun generates_a_module_that_provides_different_values_based_on_the_named_qualifier() {
        val module = Module7.create()

        assertAll {
            assertThat(module.foo1.name).isEqualTo("1")
            assertThat(module.foo2.name).isEqualTo("2")
        }
    }

    @Test
    fun generates_a_module_that_constructs_different_values_based_on_the_named_qualifier() {
        val module = Module9.create()

        assertAll {
            assertThat(module.qualifiedFoo.foo1.name).isEqualTo("1")
            assertThat(module.qualifiedFoo.foo2.name).isEqualTo("2")
        }
    }
}