package me.tatarka.inject.compiler

interface Profiler {
    fun onStart()

    fun onStop()
}