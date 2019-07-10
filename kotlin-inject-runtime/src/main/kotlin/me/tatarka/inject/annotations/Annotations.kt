package me.tatarka.inject.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.*
import kotlin.reflect.KClass

@Retention(RUNTIME)
@Target(CLASS)
annotation class Inject

@Retention(RUNTIME)
@Target(CLASS)
annotation class Module

@Retention(RUNTIME)
@Target(ANNOTATION_CLASS)
annotation class Scope

@Scope
@MustBeDocumented
@Retention(RUNTIME)
@Target(CLASS)
annotation class Singleton

@Retention(RUNTIME)
@Target(ANNOTATION_CLASS)
annotation class Qualifier

@Qualifier
@MustBeDocumented
@Retention(RUNTIME)
@Target(VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, CLASS)
annotation class Named(val value: String)

@Target(ANNOTATION_CLASS)
annotation class MultibindsKey

@MultibindsKey
annotation class ClassKey(val value: KClass<*>)

@MultibindsKey
annotation class StringKey(val value: String)

@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoSet

@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoMap

@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoFun
