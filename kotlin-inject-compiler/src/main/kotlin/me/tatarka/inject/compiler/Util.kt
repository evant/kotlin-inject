package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import me.tatarka.inject.annotations.Scope
import me.tatarka.inject.compiler.ast.AstClass
import me.tatarka.inject.compiler.ast.AstMethod
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

fun Element.scope(): AnnotationMirror? = annotationMirrors.find {
    it.annotationType.asElement().getAnnotation(Scope::class.java) != null
}

fun Element.scopeType(): TypeElement? = scope()?.let { it.annotationType.asElement() as TypeElement }

fun AstClass.scopeType(): TypeElement? = element.scopeType()

fun AstMethod.scopeType(): TypeElement? = element.scopeType()

fun String.asScopedProp(): String = "_" + decapitalize()

fun TypeName.javaToKotlinType(): TypeName = if (this is ParameterizedTypeName) {
    (rawType.javaToKotlinType() as ClassName).parameterizedBy(
        *typeArguments.map { it.javaToKotlinType() }.toTypedArray()
    )
} else {
    when(toString()) {
        "java.util.Map" -> ClassName.bestGuess("kotlin.collections.Map")
        "java.util.Set" -> ClassName.bestGuess("kotlin.collections.Set")
        "java.lang.String" -> ClassName.bestGuess("kotlin.String")
        "kotlin.jvm.functions.Function0" -> ClassName.bestGuess("kotlin.Function0")
        "kotlin.jvm.functions.Function1" -> ClassName.bestGuess("kotlin.Function1")
        else -> this
    }
}

val TypeElement.metadata: KmClass? get() {
    val meta = getAnnotation(Metadata::class.java) ?: return null
    val header = KotlinClassHeader(
        kind =  meta.kind,
        bytecodeVersion = meta.bytecodeVersion,
        data1 = meta.data1,
        data2 = meta.data2,
        extraInt = meta.extraInt,
        extraString = meta.extraString,
        metadataVersion = meta.metadataVersion,
        packageName = meta.packageName
    )
    val metadata = KotlinClassMetadata.read(header) ?: return null
    if (metadata !is KotlinClassMetadata.Class) return null
    return metadata.toKmClass()
}
