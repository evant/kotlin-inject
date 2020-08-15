package me.tatarka.inject.compiler.kapt

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.metadata.*
import kotlinx.metadata.jvm.*
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.*
import javax.lang.model.util.SimpleTypeVisitor7

fun Element.hasAnnotation(className: String): Boolean {
    return annotationMirrors.any { it.annotationType.toString() == className }
}

fun Element.typeAnnotatedWith(className: String) =
    annotationMirrors.find {
        it.annotationType.asElement().hasAnnotation(className)
    }?.annotationType?.asElement() as? TypeElement

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
    val sig = property.getterSignature
    return sig != null && matches(sig)
}

fun ExecutableElement.matches(function: KmFunction): Boolean {
    val sig = function.signature
    return sig != null && matches(sig)
}

fun ExecutableElement.matches(signature: JvmMethodSignature): Boolean {
    if (!nameMatches(signature)) {
        return false
    }
    //ex: (Lme/tatarka/inject/test/Bar;)V
    var index = 1 // skip initial (
    val desc = signature.desc

    fun match(param: TypeMirror): Boolean {
        if (index == -1) {
            return false
        }
        val c = desc[index]
        index++
        return when (c) {
            'L' -> {
                val className = desc.substring(index, desc.indexOf(';', index))
                    .replace('/', '.')
                index += className.length + 1 // ;
                if (param is DeclaredType) {
                    param.asElement().toString() == className
                } else {
                    false
                }
            }
            'Z' -> {
                param.kind == TypeKind.BOOLEAN
            }
            'B' -> {
                param.kind == TypeKind.BYTE
            }
            'C' -> {
                param.kind == TypeKind.CHAR
            }
            'S' -> {
                param.kind == TypeKind.SHORT
            }
            'I' -> {
                param.kind == TypeKind.INT
            }
            'J' -> {
                param.kind == TypeKind.LONG
            }
            'F' -> {
                param.kind == TypeKind.FLOAT
            }
            'D' -> {
                param.kind == TypeKind.DOUBLE
            }
            '[' -> {
                if (param is ArrayType) {
                    match(param.componentType)
                } else {
                    false
                }
            }
            ')' -> {
                index = -1 // mark end
                false
            }
            else -> throw IllegalStateException("Unknown descriptor: $c in {$desc}")
        }
    }

    for (param in parameters) {
        if (!match(param.asType())) {
            return false
        }
    }
    // ensure we consumed all params
    if (desc[index] != ')') {
        return false
    }

    return true
}

private fun ExecutableElement.nameMatches(signature: JvmMemberSignature?): Boolean {
    if (signature == null) return false
    return simpleName.contentEquals(signature.name)
}

fun KmClassifier.asClassName() = when (this) {
    is KmClassifier.Class -> name.asClassName()
    is KmClassifier.TypeAlias -> name.asClassName()
    is KmClassifier.TypeParameter -> null
}

fun kotlinx.metadata.ClassName.asClassName(): ClassName {
    val split = lastIndexOf('/')
    return if (split == -1) {
        ClassName("", this)
    } else {
        ClassName(substring(0, split).replace('/', '.'), substring(split + 1))
    }
}

fun TypeMirror.asTypeName(kmType: KmType?): TypeName {
    if (kmType != null) {
        val typeName = kmType.asTypeName()
        if (typeName != null) {
            return typeName
        }
    }
    return asTypeName()
}

val KmClass.packageName: String get() = name.packageName

val KmType.packageName: String get() {
    val abbreviatedType = abbreviatedType
    if (abbreviatedType != null) {
        return abbreviatedType.packageName
    }
    return when (val c = classifier) {
        is KmClassifier.Class -> c.name.packageName
        is KmClassifier.TypeAlias -> c.name.packageName
        is KmClassifier.TypeParameter -> ""
    }
}

val KmType.simpleName: String get() {
    val abbreviatedType = abbreviatedType
    if (abbreviatedType != null) {
        return abbreviatedType.simpleName
    }
    return when (val c = classifier) {
        is KmClassifier.Class -> c.name.simpleName
        is KmClassifier.TypeAlias -> c.name.simpleName
        is KmClassifier.TypeParameter -> ""
    }
}

val kotlinx.metadata.ClassName.packageName: String get() {
    val split = lastIndexOf('/')
    return if (split == -1) {
        ""
    } else {
        substring(0, split).replace('/', '.')
    }
}

val kotlinx.metadata.ClassName.simpleName: String get() {
    val split = lastIndexOf('/')
    return substring(split + 1)
}

fun KmType.asTypeName(): TypeName? {
    val abbreviatedType = abbreviatedType
    if (abbreviatedType != null) {
        return abbreviatedType.asTypeName()
    }
    val isNullable =  Flag.Type.IS_NULLABLE(flags)
    val className = classifier.asClassName() ?: return null
    return if (arguments.isEmpty()) {
        className
    } else {
        className.parameterizedBy(arguments.map { it.type!!.asTypeName()!! })
    }.copy(nullable = isNullable)
}