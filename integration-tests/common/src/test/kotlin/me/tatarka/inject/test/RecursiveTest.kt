package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Inject
import kotlin.reflect.KClass
import kotlin.test.Test

class LazyCycleFoo(val bar: LazyCycleBar)

class LazyCycleBar(val foo: Lazy<LazyCycleFoo>)

class FBar(val foo: () -> FFoo)

class FFoo(val bar: FBar)

@Inject
class CycleFoo(val bar: CycleBar)

@Inject
class CycleBar(val foo: () -> CycleFoo)

abstract class CycleComponent {
    abstract val bar: CycleBar
}

// TODO: actually generate this
class InjectCycleComponent : CycleComponent() {
    override val bar: CycleBar
        get() {
            lateinit var bar: CycleBar
            return CycleBar { CycleFoo(bar) }.also { bar = it }
        }
}

fun KClass<CycleComponent>.create() = InjectCycleComponent()

abstract class LazyCycleComponent {
    abstract val bar: LazyCycleBar

    fun bar(foo: Lazy<LazyCycleFoo>) = LazyCycleBar(foo)

    fun foo(bar: LazyCycleBar) = LazyCycleFoo(bar)
}

// TODO: actually generate this
class InjectLazyCycleComponent : LazyCycleComponent() {
    override val bar: LazyCycleBar
        get() {
            lateinit var bar: LazyCycleBar
            return bar(lazy { foo(bar) }).also { bar = it }
        }
}

fun KClass<LazyCycleComponent>.create() = InjectLazyCycleComponent()

abstract class FunctionCycleComponent {
    abstract val bar: FBar

    // scoped?
    fun bar(foo: () -> FFoo) = FBar(foo)

    // scoped?
    fun foo(bar: FBar) = FFoo(bar)
}

// TODO: actually generate this
class InjectFunctionCycleComponent : FunctionCycleComponent() {
    override val bar: FBar
        get() {
            lateinit var bar: FBar
            return bar { foo(bar) }.also { bar = it }
        }
}

fun KClass<FunctionCycleComponent>.create() = InjectFunctionCycleComponent()

class RecursiveTest {

    @Test
    fun generates_a_component_that_provides_data_recursively() {
        val component = CycleComponent::class.create()
        val bar = component.bar

        assertThat(bar).isSameAs(bar.foo().bar)
    }

    @Test
    fun generates_a_component_that_provides_a_lazy_dea_recursively() {
        val component = LazyCycleComponent::class.create()
        val bar = component.bar

        assertThat(bar).isSameAs(bar.foo.value.bar)
    }

    @Test
    fun generates_a_component_that_provides_a_function_dea_recursively() {
        val component = FunctionCycleComponent::class.create()
        val bar = component.bar

        assertThat(bar).isSameAs(bar.foo().bar)
    }
}
