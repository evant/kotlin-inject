package me.tatarka.inject.compiler.ksp

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import me.tatarka.inject.annotations.Scope
import org.jetbrains.kotlin.ksp.symbol.*
import kotlin.reflect.KClass


fun <T : Annotation> KSAnnotated.typeAnnotatedWith(type: KClass<T>): KSType? {
    for (annotation in annotations) {
        val t = annotation.annotationType.resolve()
        val a = t?.declaration?.getAnnotation(type)
        if (a != null) {
            return t
        }
    }
    return null
}

inline fun <reified T : Annotation> KSDeclaration.typeAnnotatedWith() = typeAnnotatedWith(T::class)

fun KSAnnotated.getAnnotation(type: KClass<out Annotation>): KSAnnotation? {
    return annotations.find { it.annotationType.resolve()?.declaration?.qualifiedName?.asString() == type.qualifiedName }
}

fun KSClassDeclaration.scopeType() = typeAnnotatedWith<Scope>()

fun KSDeclaration.asTypeName(): TypeName {
    val name = qualifiedName!!
    return ClassName(name.getQualifier(), name.getShortName())
}
