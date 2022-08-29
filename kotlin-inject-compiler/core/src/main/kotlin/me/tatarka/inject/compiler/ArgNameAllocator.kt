package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.NameAllocator

internal class ArgNameAllocator {

    private var nameAllocator = NameAllocator()

    fun newName(index: Int): String = nameAllocator.newName(suggestion = "arg$index")

    fun reset() {
        nameAllocator = NameAllocator()
    }
}
