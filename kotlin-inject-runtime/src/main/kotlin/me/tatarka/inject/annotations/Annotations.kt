package me.tatarka.inject.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.*

@Retention(RUNTIME)
@Target(CLASS, FUNCTION, CONSTRUCTOR)
annotation class Inject

@Retention(RUNTIME)
@Target(CLASS, VALUE_PARAMETER)
annotation class Component

@Retention(RUNTIME)
@Target(FUNCTION, PROPERTY_GETTER)
annotation class Provides

@Retention(RUNTIME)
@Target(ANNOTATION_CLASS)
annotation class Scope

@Retention(RUNTIME)
@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoSet

@Retention(RUNTIME)
@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoMap

