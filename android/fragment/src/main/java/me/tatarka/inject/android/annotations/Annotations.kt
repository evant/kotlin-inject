package me.tatarka.inject.android.annotations

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class GenerateFragmentFactory(val default: Boolean = false)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class IntoComponent(val value: KClass<Any>)