package me.tatarka.inject

class Property<T> {
    internal object UNINITIALIZED_VALUE

    @Volatile
    private var value: Any? = UNINITIALIZED_VALUE

    // final field is required to enable safe publication of constructed instance
    private val lock = this

    private val foo by lazy { }

    fun get(f: () -> T): T {
        val _v1 = value
        if (_v1 !== UNINITIALIZED_VALUE) {
            @Suppress("UNCHECKED_CAST")
            return _v1 as T
        }
        return synchronized(lock) {
            val _v2 = value
            if (_v2 !== UNINITIALIZED_VALUE) {
                @Suppress("UNCHECKED_CAST") (_v2 as T)
            } else {
                val typedValue = f()
                value = typedValue
                typedValue
            }
        }
    }
}