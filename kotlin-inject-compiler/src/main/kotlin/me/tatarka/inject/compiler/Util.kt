package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import me.tatarka.inject.annotations.Scope
import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.NoType
import javax.lang.model.util.Types

fun Element.scope(): AnnotationMirror? = annotationMirrors.find {
    it.annotationType.asElement().getAnnotation(Scope::class.java) != null
}

fun Element.scopeType(): TypeElement? = scope()?.let { it.annotationType.asElement() as TypeElement }

fun Name.asScopedProp(): String = "_" + toString().decapitalize()

fun TypeElement.recurseParents(typeUtils: Types, f: (DeclaredType, TypeElement) -> Unit) {
    f(asType() as DeclaredType, this)
    val superclass = superclass
    if (superclass.toString() != "java.lang.Object" && superclass !is NoType) {
        f(superclass as DeclaredType, typeUtils.asElement(superclass) as TypeElement)
    }
    for (iface in interfaces) {
        f(iface as DeclaredType, typeUtils.asElement(iface) as TypeElement)
    }
}

fun ExecutableElement.isExtension() = parameters.isNotEmpty() && parameters[0].simpleName.startsWith("\$this")

fun TypeName.javaToKotlinType(): TypeName = if (this is ParameterizedTypeName) {
    (rawType.javaToKotlinType() as ClassName).parameterizedBy(
        *typeArguments.map { it.javaToKotlinType() }.toTypedArray()
    )
} else {
    when(toString()) {
        "java.util.Map" -> ClassName.bestGuess("kotlin.collections.Map")
        "java.util.Set" -> ClassName.bestGuess("kotlin.collections.Set")
        "java.lang.String" -> ClassName.bestGuess("kotlin.String")
        else -> this
    }
}