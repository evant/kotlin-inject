package me.tatarka.inject.internal

expect class LazyMap {
    fun <T> get(key: String, init: () -> T): T
}
