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
annotation class IntoSet(val multiple: Boolean = false)

@Target(FUNCTION, PROPERTY_GETTER)
annotation class IntoMap(val multiple: Boolean = false)

@Target(VALUE_PARAMETER)
annotation class Assisted

@Target(CLASS)
annotation class AssistedFactory(
    /**
     * Indicates that a top level function should be called as an underlying factory.
     * If set it will behave similar to function injection.
     * Can be a function name if the function is in the same package or a full name with package otherwise.
     */
    val injectFunction: String = ""
)

@Target(ANNOTATION_CLASS)
annotation class Qualifier
