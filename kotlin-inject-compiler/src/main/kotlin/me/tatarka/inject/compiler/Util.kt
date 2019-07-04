package me.tatarka.inject.compiler

import me.tatarka.inject.annotations.Scope
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.Name

fun Element.scope(): AnnotationMirror? = annotationMirrors.find {
    it.annotationType.asElement().getAnnotation(Scope::class.java) != null
}

fun Name.asScopedProp(): String = "_" + toString().decapitalize()
