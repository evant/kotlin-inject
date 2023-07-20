package me.tatarka.inject.internal

private val NULL = Any()

actual class LazyMap {
    private val map = mutableMapOf<String, Any>()

    actual fun <T> get(key: String, init: () -> T): T {
        val cachedResult = map[key]
        return if (cachedResult == null) {
            val result = init() ?: NULL
            map[key] = result
            coerceResult(result)
        } else {
            coerceResult(cachedResult)
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