package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.containsOnly
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Module
import org.junit.Test

data class FooValue(val name: String)

@Module abstract class SetModule {
    abstract val items: Set<FooValue>

    @IntoSet
    fun fooValue1() = FooValue("1")

    @IntoSet
    val fooValue2
        get() = FooValue("2")
}

@Module abstract class DynamicKeyModule {

    abstract val items: Map<String, FooValue>

    @IntoMap
    fun fooValue1() = "1" to FooValue("1")

    @IntoMap
    val fooValue2
        get() = "2" to FooValue("2")
}

class MultibindsTest {

    @Test
    fun generates_a_module_that_provides_multiple_items_into_a_set() {
        val module = SetModule::class.create()

        assertThat(module.items).containsOnly(FooValue("1"), FooValue("2"))
    }

    @Test
    fun generates_a_module_that_provides_multiple_items_into_a_map() {
        val module = DynamicKeyModule::class.create()

        assertThat(module.items).containsOnly(
            "1" to FooValue("1"),
            "2" to FooValue("2")
        )
    }

//    @Test
//    fun generates_a_module_that_provides_multiple_items_into_a_fun() {
//        val module = FunKeyModule::class.create()
//
//        assertAll {
//            assertThat(module.items("1")).isEqualTo(FooValue("1"))
//            assertThat(module.items("2")).isEqualTo(FooValue("2"))
//        }
//    }
}