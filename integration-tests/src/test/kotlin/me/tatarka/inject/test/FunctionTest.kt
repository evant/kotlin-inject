package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import kotlin.test.Test

@Inject class FunctionFoo

@Module abstract class FunctionModule {
    abstract val fooProvider: () -> FunctionFoo
}

@Inject class PartialFunctionBar(val foo: FunctionFoo, val name: String)

@Module abstract class PartialFunctionModule {
    abstract val barFactory: (name: String) -> PartialFunctionBar
}

@Inject data class ArityFunctionBar2(val arg1: String, val arg2: String)

@Inject data class ArityFunctionBar3(val arg1: String, val arg2: String, val arg3: String)

@Inject data class ArityFunctionBar4(val arg1: String, val arg2: String, val arg3: String, val arg4: String)

@Module abstract class FunctionArityModule {
    abstract val arity2: (arg1: String, arg2: String) -> ArityFunctionBar2
    abstract val arity3: (arg1: String, arg2: String, arg3: String) -> ArityFunctionBar3
    abstract val arity4: (arg1: String, arg2: String, arg3: String, arg4: String) -> ArityFunctionBar4
}

class FunctionTest {

    @Test fun generates_a_module_that_provides_a_function_that_returns_a_dep() {
        val module = FunctionModule::class.create()

        assertThat(module.fooProvider()).isNotNull()
    }

    @Test fun generates_a_module_that_provides_a_function_that_partially_creates_a_dep() {
        val module = PartialFunctionModule::class.create()

        assertThat(module.barFactory("name").name).isEqualTo("name")
    }

    @Test fun generates_a_module_that_provides_functions_of_various_arities() {
        val module = FunctionArityModule::class.create()

        assertThat(module.arity2("1", "2")).isEqualTo(ArityFunctionBar2("1", "2"))
        assertThat(module.arity3("1", "2", "3")).isEqualTo(ArityFunctionBar3("1", "2", "3"))
        assertThat(module.arity4("1", "2", "3", "4")).isEqualTo(ArityFunctionBar4("1", "2", "3", "4"))
    }
}