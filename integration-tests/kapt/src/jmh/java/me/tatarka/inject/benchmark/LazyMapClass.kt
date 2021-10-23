package me.tatarka.inject.benchmark

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

private val NULL = Any()

class LazyMapClass {
    private val map = ConcurrentHashMap<KClass<*>, Any>()

    fun <T> get(key: KClass<*>, init: () -> T): T {
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
