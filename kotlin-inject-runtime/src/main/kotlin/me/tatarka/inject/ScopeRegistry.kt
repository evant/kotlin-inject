package me.tatarka.inject

import me.tatarka.inject.annotations.Qualifier
import kotlin.reflect.KClass

class ScopeRegistry {
    private val instances = mutableMapOf<Pair<Qualifier?, KClass<*>>, Any>()

    inline fun <reified T : Any> get(qualifier: Qualifier?, noinline f: () -> T): T = get(qualifier, T::class, f)

    fun <T : Any> get(qualifier: Qualifier?, klass: KClass<T>, f: () -> T): T {
        return instances.computeIfAbsent(qualifier to klass) { f() } as T
    }
}