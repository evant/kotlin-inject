package me.tatarka.inject.compiler.kapt

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import me.tatarka.inject.annotations.Scope
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

fun <T : Annotation> Element.typeAnnotatedWith(type: KClass<T>) =
    annotationMirrors.find {
        it.annotationType.asElement().getAnnotation(type.java) != null
    }?.annotationType?.asElement() as? TypeElement

inline fun <reified T : Annotation> Element.typeAnnotatedWith() = typeAnnotatedWith(T::class)

fun Element.scopeType(): TypeElement? = typeAnnotatedWith<Scope>()

fun TypeName.javaToKotlinType(): TypeName = if (this is ParameterizedTypeName) {
    (rawType.javaToKotlinType() as ClassName).parameterizedBy(
        *typeArguments.map { it.javaToKotlinType() }.toTypedArray()
    )
} else {
    val s = toString()
    when (s) {
        "java.util.Map" -> ClassName.bestGuess("kotlin.collections.Map")
        "java.util.Set" -> ClassName.bestGuess("kotlin.collections.Set")
        "java.lang.String" -> ClassName.bestGuess("kotlin.String")
        else -> {
            val regex = Regex("kotlin\\.jvm\\.functions\\.(Function[0-9]+)")
            val result = regex.matchEntire(s)
            if (result != null) {
                ClassName.bestGuess("kotlin.${result.groupValues[1]}")
            } else {
                this
            }
        }
    }
}

val TypeElement.metadata: KmClass?
    get() {
        val meta = getAnnotation(Metadata::class.java) ?: return null
        val header = KotlinClassHeader(
            kind = meta.kind,
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
