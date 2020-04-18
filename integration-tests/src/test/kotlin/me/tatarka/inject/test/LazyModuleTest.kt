package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import kotlin.test.Test

@Inject class LazyFoo

@Module abstract class LazyModule {
    abstract val foo: Lazy<LazyFoo>
}

class LazyModuleTest {

    @Test
    fun generates_a_module_that_provides_a_lazy_dep() {
        val module = LazyModule::class.create()

        val foo = module.foo

        assertThat(foo.value).isSameAs(foo.value)
    }
}
