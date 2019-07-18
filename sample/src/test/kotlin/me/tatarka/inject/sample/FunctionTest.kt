package me.tatarka.inject.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import org.junit.Test

@Inject class FunctionFoo

@Module abstract class FunctionModule {
    abstract val fooProvider: () -> FunctionFoo

    companion object
}

@Inject class PartialFunctionBar(val foo: FunctionFoo, val name: String)

@Module abstract class PartialFunctionModule {
    abstract val barFactory: (name: String) -> PartialFunctionBar

    companion object
}

class FunctionTest {

    @Test fun generates_a_module_that_provides_a_function_that_returns_a_dep() {
        val module = FunctionModule.create()

        assertThat(module.fooProvider()).isNotNull()
    }

    @Test fun generates_a_module_that_provides_a_function_that_partially_creates_a_dep() {
        val module = PartialFunctionModule.create()

        assertThat(module.barFactory("name").name).isEqualTo("name")
    }
}