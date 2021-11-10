package me.tatarka.inject.internal

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isSameAs
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test

class LazyMapTestNative {

    @Test
    fun caches_value_from_multiple_threads() {
        val lazyMap = LazyMap()
        val count = 100
        val results = List(count) {
            Worker.start().execute(TransferMode.SAFE, { lazyMap }) {
                it.get("test") { Any() }
            }
        }.map { it.result }

        assertThat(results).each {
            it.isSameAs(results.first())
        }
    }
}