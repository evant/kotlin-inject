package me.tatarka.inject.compiler

import assertk.assertThat
import kotlin.test.Test

internal class ArgNameAllocatorTest {

    @Test
    fun `same indices lead to different arg names`() {
        val allocator = ArgNameAllocator()
        allocator.assert(1, "arg1")
        allocator.assert(1, "arg1_")
    }

    @Test
    fun `reset leads to same arg names`() {
        val allocator = ArgNameAllocator()
        allocator.assert(1, "arg1")
        allocator.reset()
        allocator.assert(1, "arg1")
    }

    @Test
    fun `different indices lead to different arg names`() {
        val allocator = ArgNameAllocator()
        allocator.assert(0, "arg0")
        allocator.assert(1, "arg1")
    }

    private fun ArgNameAllocator.assert(index: Int, expected: String) {
        assertThat(newName(index), "arg$expected")
    }
}