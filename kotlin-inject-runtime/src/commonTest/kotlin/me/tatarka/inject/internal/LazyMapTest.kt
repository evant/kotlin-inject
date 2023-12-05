package me.tatarka.inject.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class LazyMapTest {
    @Test
    fun caches_value_between() {
        val lazyMap = LazyMap()
        var calls = 0
        val value1 = lazyMap.get("key") {
            calls++
            Any()
        }
        val value2 = lazyMap.get("key") {
            calls++
            Any()
        }

        assertThat(calls).isEqualTo(1)
        assertThat(value1).isSameInstanceAs(value2)
    }
}