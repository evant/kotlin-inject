package me.tatarka.inject

import kotlin.reflect.KClass

inline fun <M : Any> KClass<M>.createModule(): M = throw Exception("No inject module found for: ${this}")