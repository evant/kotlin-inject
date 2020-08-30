package me.tatarka.inject.internal

import java.util.concurrent.ConcurrentHashMap

private val NULL = Any()

interface ScopedComponent {
    val _scoped: ConcurrentHashMap<String, Any>

    fun <T> _lazyGet(key: String, init: () -> T): T {
        val result = _scoped[key]
        if (result == null) {
            synchronized(_scoped) {
                var result = _scoped[key]
                if (result == null) {
                    result = init() ?: NULL
                    _scoped[key] = result
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