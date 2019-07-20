package me.tatarka.inject.sample

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import me.tatarka.inject.annotations.Named
import org.junit.Test

class NamedFoo(val name: String)

@Module abstract class ProvidesNamedModule {
    abstract val foo1: @Named("1") NamedFoo

    abstract val foo2: @Named("2") NamedFoo

    fun foo1(): @Named("1") NamedFoo = NamedFoo("1")
    fun foo2(): @Named("2") NamedFoo = NamedFoo("2")

    companion object
}

@Inject class QualifiedFoo(val foo1: @Named("1") NamedFoo, val foo2: @Named("2") NamedFoo)

@Module abstract class ConstructorNamedModule {
    abstract val qualifiedFoo: QualifiedFoo

    fun foo1(): @Named("1") NamedFoo = NamedFoo("1")
    fun foo2(): @Named("2") NamedFoo = NamedFoo("2")

    companion object
}

class QualifierTest {

    @Test
    fun generates_a_module_that_provides_different_values_based_on_the_named_qualifier() {
        val module = ProvidesNamedModule.create()

        assertAll {
            assertThat(module.foo1.name).isEqualTo("1")
            assertThat(module.foo2.name).isEqualTo("2")
        }
    }

    @Test
    fun generates_a_module_that_constructs_different_values_based_on_the_named_qualifier() {
        val module = ConstructorNamedModule.create()

        assertAll {
            assertThat(module.qualifiedFoo.foo1.name).isEqualTo("1")
            assertThat(module.qualifiedFoo.foo2.name).isEqualTo("2")
        }
    }
}