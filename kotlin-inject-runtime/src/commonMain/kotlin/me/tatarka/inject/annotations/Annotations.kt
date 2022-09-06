package me.tatarka.inject.annotations

import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.CONSTRUCTOR
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@Target(CLASS, FUNCTION, CONSTRUCTOR)
annotation class Inject

@Target(CLASS, VALUE_PARAMETER)
annotation class Component

@Target(FUNCTION, PROPERTY_GETTER)
annotation class Provides

@Target(ANNOTATION_CLASS)
annotation class Scope

@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoSet

@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoMap

@Target(VALUE_PARAMETER)
annotation class Assisted