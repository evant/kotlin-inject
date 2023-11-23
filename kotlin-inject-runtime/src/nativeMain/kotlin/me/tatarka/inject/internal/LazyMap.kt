package me.tatarka.inject.internal

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

private val NULL = Any()

actual class LazyMap {
    private val map = mutableMapOf<String, Any>()
    private val lock = ReentrantLock()

    actual fun <T> get(key: String, init: () -> T): T {
        return lock.withLock {
            var result = map[key]
            if (result == null) {
                result = init() ?: NULL
                map[key] = result
            }
            coerceResult(result)
        }
    }

    private fun <T> coerceResult(result: Any): T {
        @Suppress("UNCHECKED_CAST")
        return if (result === NULL) {
            null
        } else {
            result
        } as T
    }
}
