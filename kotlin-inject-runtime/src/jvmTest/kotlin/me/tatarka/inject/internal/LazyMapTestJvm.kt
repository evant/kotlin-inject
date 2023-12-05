package me.tatarka.inject.internal

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isSameInstanceAs
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test

class LazyMapTestJvm {
    @Test
    fun caches_value_from_multiple_threads() {
        val lazyMap = LazyMap()
        val count = 100
        val executors = Executors.newFixedThreadPool(count)
        val results = executors.invokeAll(List(count) {
            Callable {
                lazyMap.get("test") { Any() }
            }
        }).map { it.get() }
        executors.shutdown()

        assertThat(results).each {
            it.isSameInstanceAs(results.first())
        }
    }
}