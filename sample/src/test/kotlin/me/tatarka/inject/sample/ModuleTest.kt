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

@Module abstract class Module4 {
    abstract val foo: IFoo

    @get:Binds
    abstract val Foo.binds: IFoo

    companion object
}


class NamedFoo(val name: String)

@Module abstract class Module7 {
    @get:Named("1")
    abstract val foo1: NamedFoo

    @get:Named("2")
    abstract val foo2: NamedFoo

    @Provides @Named("1") fun foo1() = NamedFoo("1")
    @Provides @Named("2") fun foo2() = NamedFoo("2")

    companion object
}

@Inject class Foo2 : IFoo

@Module abstract class Module8 {
    @get:Named("1")
    abstract val foo1: IFoo

    @get:Named("2")
    abstract val foo2: IFoo

    @get:Binds
    @get:Named("1")
    abstract val Foo.binds: IFoo

    @get:Binds
    @get:Named("2")
    abstract val Foo2.binds: IFoo

    companion object
}

@Inject class QualifiedFoo(@Named("1") val foo1: NamedFoo, @Named("2") val foo2: NamedFoo)

@Module abstract class Module9 {
    abstract val qualifiedFoo: QualifiedFoo

    @Provides @Named("1") fun foo1() = NamedFoo("1")
    @Provides @Named("2") fun foo2() = NamedFoo("2")

    companion object
}

class ModuleTest {

    @Before
    fun setup() {
        barConstructorCount = 0
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
    fun generates_a_module_that_binds_an_interface_to_a_dep() {
        val module = Module4.create()

        assertThat(module.foo).isInstanceOf(Foo::class)
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
    fun generates_a_module_that_binds_different_values_based_on_the_named_qualifier() {
        val module = Module8.create()

        assertAll {
            assertThat(module.foo1).hasClass(Foo::class)
            assertThat(module.foo2).hasClass(Foo2::class)
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