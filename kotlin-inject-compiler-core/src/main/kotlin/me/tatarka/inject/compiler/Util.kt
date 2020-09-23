package me.tatarka.inject.compiler

fun <T> Iterable<T>.eqvItr(other: Iterable<T>, eqv: (T, T) -> Boolean): Boolean {
    val itr1 = iterator()
    val itr2 = other.iterator()
    while (itr1.hasNext()) {
        if (!itr2.hasNext()) {
            return false
        }
        val v1 = itr1.next()
        val v2 = itr2.next()
        if (!eqv(v1, v2)) {
            return false
        }
    }
    if (itr2.hasNext()) {
        return false
    }
    return true
}

fun <T : Any> T?.eqv(other: T?, eqv: (T, T) -> Boolean): Boolean =
    (this == null && other == null) ||
            (this != null && other != null && eqv(this, other))

class HashCollector() {
    var hash: Int = 1
        private set

    fun hash(arg: Any?) {
        hash = 31 * hash + arg.hashCode()
    }
}

inline fun collectHash(collector: HashCollector = HashCollector(), block: HashCollector.() -> Unit): Int =
    collector.apply(block).hash