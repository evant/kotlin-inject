package me.tatarka.inject.compiler

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

internal class ArgNameAllocatorTest {

    @Test
    fun `same indices lead to different arg names`() {
        val allocator = ArgNameAllocator()
        assertThat(allocator.newName(1)).isEqualTo("arg1")
        assertThat(allocator.newName(1)).isEqualTo("arg1_")
    }

    @Test
    fun `reset leads to same arg names`() {
        val allocator = ArgNameAllocator()
        assertThat(allocator.newName(1)).isEqualTo("arg1")
        allocator.reset()
        assertThat(allocator.newName(1)).isEqualTo("arg1")
    }

    @Test
    fun `different indices lead to different arg names`() {
        val allocator = ArgNameAllocator()
        assertThat(allocator.newName(0)).isEqualTo("arg0")
        assertThat(allocator.newName(1)).isEqualTo("arg1")
    }
}
