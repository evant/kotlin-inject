package me.tatarka.inject.internal

import java.util.concurrent.ConcurrentMap

fun <T> ConcurrentMap<String, Any?>.lazyGet(key: String, init: () -> T): T {
    if (!containsKey(key)) {
        synchronized(this) {
            if (!containsKey(key)) {
                val result = init()
                put(key, result)
                return result
            }
        }
    }
    return get(key) as T
}