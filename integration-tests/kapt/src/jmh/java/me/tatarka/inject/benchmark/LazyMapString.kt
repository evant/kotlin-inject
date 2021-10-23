package me.tatarka.inject.benchmark

import java.util.concurrent.ConcurrentHashMap

private val NULL = Any()

class LazyMapString {
    private val map = ConcurrentHashMap<String, Any>()

    fun <T> get(key: String, init: () -> T): T {
        val result = map[key]
        if (result == null) {
            synchronized(map) {
                var result = map[key]
                if (result == null) {
                    result = init() ?: NULL
                    map[key] = result
                }
                return coerceResult(result)
            }
        } else {
            return coerceResult(result)
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
