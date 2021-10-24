package me.tatarka.inject.compiler

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.tableOf
import kotlin.test.Test

class AccessorTest {
    @Test
    fun resolve() {
        tableOf("current", "subject", "result")
            .row(Accessor.Empty, Accessor.Empty, Accessor.Empty)
            .row(Accessor.Empty, Accessor("a", "b"), Accessor("a", "b"))
            .row(Accessor("a"), Accessor("a", "b"), Accessor("b"))
            .row(Accessor("a"), Accessor("a", "b", "c"), Accessor("b", "c"))
            .row(Accessor("a", "b"), Accessor("a", "b", "c"), Accessor("c"))
            .row(Accessor("a"), Accessor("b", "c"), Accessor("b", "c"))
            .row(Accessor("a", "b", "c"), Accessor("a", "b"), Accessor("a", "b"))
            .row(Accessor("a"), Accessor.Empty, Accessor.Empty)
            .forAll { current, subject, result ->
                assertThat(current.resolve(subject)).isEqualTo(result)
            }
    }
}