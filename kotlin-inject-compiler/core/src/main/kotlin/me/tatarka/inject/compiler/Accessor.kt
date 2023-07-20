package me.tatarka.inject.compiler

fun Accessor(vararg components: String): Accessor = Accessor(components.toList())

data class Accessor(val components: List<String>) {
    override fun toString(): String = components.joinToString(".")

    companion object {
        val Empty = Accessor(emptyList())
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun isNotEmpty() = components.isNotEmpty()

    inline val size: Int
        get() = components.size

    operator fun plus(part: String): Accessor {
        return Accessor(components + part)
    }

    /**
     * Resolves the given accessor in the scope of this accessor.
     * ex:
     * ```
     * "".resolve("a.b") -> "a.b"
     * "a".resolve("a.b") -> "b"
     * "a".resolve("a.b.c") -> "b.c"
     * "a.b".resolve("a.b.c") -> "c"
     * "a".resolve("b.c") -> "b.c"
     * "a.b.c".resolve("a.b") -> "a.b"
     * ```
     */
    fun resolve(accessor: Accessor): Accessor {
        if (accessor.size < size) {
            return accessor
        }
        for (i in components.indices) {
            if (i >= accessor.size) {
                return accessor
            }
            if (components[i] != accessor.components[i]) {
                return accessor.sublist(i)
            }
        }
        return accessor.sublist(size)
    }

    private fun sublist(startIndex: Int): Accessor {
        return if (startIndex == 0) {
            this
        } else {
            Accessor(components.subList(startIndex, size))
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Accessor?.orEmpty(): Accessor = this ?: Accessor.Empty