package me.tatarka.inject.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.CONSTRUCTOR
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

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
