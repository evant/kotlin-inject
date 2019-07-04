package me.tatarka.inject.sample

import assertk.assertThat
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import me.tatarka.inject.annotations.Module
import me.tatarka.inject.annotations.Provides
import org.junit.Test

class ProvidesFoo

@Module abstract class ProvidesFunctionModule {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun foo() = ProvidesFoo().also { providesCalled = true }

    companion object
}

@Module abstract class ProvidesValModule {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @get:Provides
    val provideFoo
        get() = ProvidesFoo().also { providesCalled = true }

    companion object
}

@Module abstract class ProvidesValConstructorModule(@get:Provides val provideFoo: ProvidesFoo) {
    abstract val foo: ProvidesFoo

    companion object
}

class ProvidesTest {

    @Test
    fun generates_a_module_that_provides_a_dep_from_a_function() {
        val module = ProvidesFunctionModule.create()

        module.foo
        assertThat(module.providesCalled).isTrue()
    }

    @Test
    fun generates_a_module_that_provides_a_dep_from_a_val() {
        val module = ProvidesValModule.create()

        module.foo
        assertThat(module.providesCalled).isTrue()
    }

    @Test
    fun generates_a_module_that_provides_a_dep_from_a_constructor_val() {
        val foo = ProvidesFoo()
        val module = ProvidesValConstructorModule.create(foo)

        assertThat(module.foo).isSameAs(foo)
    }
}