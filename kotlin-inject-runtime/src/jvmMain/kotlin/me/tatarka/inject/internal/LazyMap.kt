package me.tatarka.inject.internal

import java.util.concurrent.ConcurrentHashMap

private val NULL = Any()

actual class LazyMap {
    private val map = ConcurrentHashMap<String, Any>()

    actual fun <T> get(key: String, init: () -> T): T {
        val result = map[key]
        return if (result == null) {
            synchronized(map) {
                var result = map[key]
                if (result == null) {
                    result = init() ?: NULL
                    map[key] = result
                }
                coerceResult(result)
            }
        } else {
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