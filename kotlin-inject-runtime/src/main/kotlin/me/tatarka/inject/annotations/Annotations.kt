package me.tatarka.inject.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.*

@Retention(RUNTIME)
@Target(CLASS)
annotation class Inject

@Retention(RUNTIME)
@Target(CLASS)
annotation class Module

@Retention(RUNTIME)
@Target(ANNOTATION_CLASS)
annotation class Scope

@Retention(RUNTIME)
@Target(ANNOTATION_CLASS)
annotation class Qualifier

@Qualifier
@MustBeDocumented
@Retention(RUNTIME)
@Target(TYPE)
annotation class Named(val value: String)

@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoSet

@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoMap
