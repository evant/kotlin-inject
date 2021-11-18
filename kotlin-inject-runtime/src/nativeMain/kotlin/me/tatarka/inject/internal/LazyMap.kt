package me.tatarka.inject.internal

import kotlin.native.concurrent.SharedImmutable
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

@SharedImmutable
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
        return if (result === NULL) {
            null
        } else {
            result
        } as T
    }
}
