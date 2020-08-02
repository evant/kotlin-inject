package me.tatarka.inject.compiler.kapt

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmType
import kotlinx.metadata.jvm.*
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

fun Element.hasAnnotation(className: String): Boolean {
    return annotationMirrors.any { it.annotationType.toString() == className }
}

fun Element.typeAnnotatedWith(className: String) =
        annotationMirrors.find {
            it.annotationType.asElement().hasAnnotation(className)
        }?.annotationType?.asElement() as? TypeElement

fun TypeName.javaToKotlinType(): TypeName = if (this is ParameterizedTypeName) {
    (rawType.javaToKotlinType() as ClassName).parameterizedBy(
            *typeArguments.map { it.javaToKotlinType() }.toTypedArray()
    )
} else if (this is WildcardTypeName) {
    if (inTypes.isNotEmpty()) {
        WildcardTypeName.consumerOf(inTypes.first().javaToKotlinType())
    } else {
        WildcardTypeName.producerOf(outTypes.first().javaToKotlinType())
    }
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

val TypeElement.metadata: KotlinClassMetadata?
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
        return KotlinClassMetadata.read(header)
    }

fun KotlinClassMetadata.toKmClass() = when (this) {
    is KotlinClassMetadata.Class -> toKmClass()
    else -> null
}

fun KotlinClassMetadata.toKmPackage() = when (this) {
    is KotlinClassMetadata.FileFacade -> toKmPackage()
    is KotlinClassMetadata.MultiFileClassPart -> toKmPackage()
    else -> null
}

fun ExecutableElement.matches(property: KmProperty): Boolean {
    if (!nameMatches(property.getterSignature)) {
        return false
    }
    val receiverType = property.receiverParameterType
    return if (receiverType == null) {
        parameters.isEmpty()
    } else {
        parameters.size == 1 && parameters[0].asType().matches(receiverType)
    }
}

fun ExecutableElement.matches(function: KmFunction): Boolean {
    if (!nameMatches(function.signature)) {
        return false
    }
    val parameterTypes = mutableListOf<KmType>()
    function.receiverParameterType?.let { parameterTypes.add(it) }
    for (param in function.valueParameters) {
        param.type?.let { parameterTypes.add(it) }
    }
    if (parameterTypes.size != parameters.size) {
        return false
    }
    for (i in 0 until parameterTypes.size) {
        if (!parameters[i].asType().matches(parameterTypes[i])) {
            return false
        }
    }
    return true
}

private fun TypeMirror.matches(type: KmType): Boolean {
    return when (val classifier = type.classifier) {
        is KmClassifier.Class -> {
            asTypeName().javaToKotlinType().toString() == classifier.name.replace("/", ".")
        }
        is KmClassifier.TypeParameter -> false
        is KmClassifier.TypeAlias -> false
    }
}

private fun ExecutableElement.nameMatches(signature: JvmMemberSignature?): Boolean {
    if (signature == null) return false
    return simpleName.contentEquals(signature.name)
}
