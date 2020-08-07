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

fun DeclaredType.rawTypeName(kmType: KmType?): ClassName {
    val className =
        (kmType?.classifier as? KmClassifier.Class)?.asClassName()
            ?: (this.asElement() as TypeElement).asClassName()
    return className.kotlinClassName()
}

fun KmClassifier.Class.asClassName() = ClassName.bestGuess(name.replace('/', '.'))

fun ClassName.kotlinClassName(): ClassName {
    return when (canonicalName) {
        "java.util.Map" -> ClassName("kotlin.collections", "Map")
        "java.util.Set" -> ClassName("kotlin.collections", "Set")
        "java.lang.String" -> ClassName("kotlin", "String")
        else -> this
    }
}

fun TypeMirror.asTypeName(kmType: KmType?): TypeName {
    return accept(object : SimpleTypeVisitor7<TypeName, Void?>() {
        override fun visitPrimitive(t: PrimitiveType, p: Void?): TypeName {
            return when (t.kind) {
                TypeKind.BOOLEAN -> BOOLEAN
                TypeKind.BYTE -> BYTE
                TypeKind.SHORT -> SHORT
                TypeKind.INT -> INT
                TypeKind.LONG -> LONG
                TypeKind.CHAR -> CHAR
                TypeKind.FLOAT -> FLOAT
                TypeKind.DOUBLE -> DOUBLE
                else -> throw AssertionError()
            }
        }

        override fun visitDeclared(t: DeclaredType, p: Void?): TypeName {
            val isNullable = kmType?.let { Flag.Type.IS_NULLABLE(it.flags) } == true
            val rawType: ClassName = t.rawTypeName(kmType).copy(nullable = isNullable) as ClassName
            val enclosingType = t.enclosingType
            val enclosing = if (enclosingType.kind != TypeKind.NONE &&
                Modifier.STATIC !in t.asElement().modifiers
            )
                enclosingType.accept(this, null) else
                null
            if (t.typeArguments.isEmpty() && enclosing !is ParameterizedTypeName) {
                return rawType
            }

            val typeArgumentNames = mutableListOf<TypeName>()
            for (i in 0 until t.typeArguments.size) {
                val typeArg = t.typeArguments[i]
                val kmArg = kmType?.arguments?.getOrNull(i)?.type
                typeArgumentNames += typeArg.asTypeName(kmArg)
            }

            return if (enclosing is ParameterizedTypeName)
                enclosing.nestedClass(rawType.simpleName, typeArgumentNames) else
                rawType.parameterizedBy(typeArgumentNames)
        }

        override fun visitError(t: ErrorType, p: Void?): TypeName {
            return visitDeclared(t, p)
        }

        override fun visitArray(t: ArrayType, p: Void?): TypeName {
            return when (t.componentType.kind) {
                TypeKind.BOOLEAN -> BOOLEAN_ARRAY
                TypeKind.BYTE -> BYTE_ARRAY
                TypeKind.SHORT -> SHORT_ARRAY
                TypeKind.INT -> INT_ARRAY
                TypeKind.LONG -> LONG_ARRAY
                TypeKind.CHAR -> CHAR_ARRAY
                TypeKind.FLOAT -> FLOAT_ARRAY
                TypeKind.DOUBLE -> DOUBLE_ARRAY
                else -> ARRAY.parameterizedBy(t.componentType.asTypeName(kmType?.arguments?.getOrNull(0)?.type))
            }
        }

        override fun visitTypeVariable(
            t: TypeVariable,
            p: Void?
        ): TypeName {
            return t.asTypeVariableName()
        }

        override fun visitWildcard(t: WildcardType, p: Void?): TypeName {
            return t.asWildcardTypeName()
        }

        override fun visitNoType(t: NoType, p: Void?): TypeName {
            if (t.kind == TypeKind.VOID) return UNIT
            return super.visitUnknown(t, p)
        }

        override fun defaultAction(e: TypeMirror?, p: Void?): TypeName {
            throw IllegalArgumentException("Unexpected type mirror: " + e!!)
        }
    }, null)
}
