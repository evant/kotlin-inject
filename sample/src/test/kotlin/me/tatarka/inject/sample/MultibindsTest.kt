package me.tatarka.inject.sample

import assertk.assertThat
import assertk.assertions.containsOnly
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Module
import org.junit.Test
import kotlin.reflect.KClass

data class FooValue(val name: String)

@Module abstract class SetModule {
    abstract val items: Set<FooValue>

    @get:IntoSet
    val fooValue1
        get() = FooValue("1")

    @get:IntoSet
    val fooValue2
        get() = FooValue("2")

    companion object
}

@Module abstract class DynamicKeyModule {

    abstract val items: Map<String, FooValue>

    @IntoMap
    fun fooValue1() = "1" to FooValue("1")

    @IntoMap
    fun fooValue2() = "2" to FooValue("2")

    companion object
}

class MultibindsTest {

    @Test
    fun generates_a_module_that_provides_multiple_items_into_a_set() {
        val module = SetModule.create()

        assertThat(module.items).containsOnly(FooValue("1"), FooValue("2"))
    }

    @Test
    fun generates_a_module_that_provides_multiple_items_into_a_map() {
        val module = DynamicKeyModule.create()

        assertThat(module.items).containsOnly(
            "1" to FooValue("1"),
            "2" to FooValue("2")
        )
    }

//    @Test
//    fun generates_a_module_that_provides_multiple_items_into_a_fun() {
//        val module = FunKeyModule.create()
//
//        assertAll {
//            assertThat(module.items("1")).isEqualTo(FooValue("1"))
//            assertThat(module.items("2")).isEqualTo(FooValue("2"))
//        }
//    }
}