@file:Suppress("NOTHING_TO_INLINE", "DEPRECATION")

package me.tatarka.inject.compiler.kapt

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.tags.TypeAliasTag
import kotlinx.metadata.Flag
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmType
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.KotlinClassMetadata
import me.tatarka.inject.compiler.HashCollector
import me.tatarka.inject.compiler.collectHash
import me.tatarka.inject.compiler.eqv
import me.tatarka.inject.compiler.eqvItr
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

fun Element.hasAnnotation(packageName: String, simpleName: String): Boolean {
    val qualifiedName = if (packageName.isNotEmpty()) "$packageName.$simpleName" else simpleName
    return annotationMirrors.any { it.annotationType.toString() == qualifiedName }
}

fun Element.annotationAnnotatedWith(packageName: String, simpleName: String) =
    annotationMirrors.find {
        it.annotationType.asElement().hasAnnotation(packageName, simpleName)
    }

val TypeElement.metadata: KotlinClassMetadata?
    get() {
        val meta = getAnnotation(Metadata::class.java) ?: return null
        val header = Metadata(
            kind = meta.kind,
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

// jvm signature without the return type. Since you can't have overloads that only differ by return type we don't need
// to check it when comparing methods.
val ExecutableElement.simpleSig: String
    get() {
        val name = simpleName.toString()

        @Suppress("ComplexMethod", "NestedBlockDepth")
        fun convert(type: TypeMirror, out: StringBuilder) {
            with(out) {
                when (type.kind) {
                    TypeKind.BOOLEAN -> append('Z')
                    TypeKind.BYTE -> append('B')
                    TypeKind.CHAR -> append('C')
                    TypeKind.SHORT -> append('S')
                    TypeKind.INT -> append('I')
                    TypeKind.LONG -> append('J')
                    TypeKind.FLOAT -> append('F')
                    TypeKind.DOUBLE -> append('D')
                    TypeKind.VOID -> append('V')
                    else -> when (type) {
                        is ArrayType -> {
                            append('[')
                            convert(type.componentType, out)
                        }
                        is DeclaredType -> {
                            append('L')
                            val element = type.asElement()
                            val parts = mutableListOf<String>()
                            var parent = element
                            while (parent !is PackageElement) {
                                parts.add(parent.simpleName.toString())
                                parent = parent.enclosingElement
                            }
                            parts.reverse()
                            val packageName = parent.qualifiedName.toString()
                            if (packageName.isNotEmpty()) {
                                append(packageName.replace('.', '/'))
                                append('/')
                            }
                            append(parts.joinToString("$"))
                            append(';')
                        }
                        else -> {
                            // Generic type, erase to object
                            append("Ljava/lang/Object;")
                        }
                    }
                }
            }
        }

        return StringBuilder().apply {
            append(name)
            append('(')
            for (parm in parameters) {
                convert(parm.asType(), this)
            }
            append(')')
        }.toString()
    }

val JvmMethodSignature.simpleSig: String
    get() = name + desc.substring(0, desc.lastIndexOf(')') + 1)

private fun KmClassifier.asClassName() = name?.asClassName()

private val KmClassifier.name: String?
    get() = when (this) {
        is KmClassifier.Class -> name
        is KmClassifier.TypeAlias -> name
        is KmClassifier.TypeParameter -> null
    }

fun kotlinx.metadata.ClassName.asClassName(): ClassName {
    val split = lastIndexOf('/')
    return if (split == -1) {
        ClassName("", this)
    } else {
        ClassName(substring(0, split).replace('/', '.'), substring(split + 1).split('.'))
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

val KmType.packageName: String
    get() {
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

val KmType.simpleName: String
    get() {
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

val kotlinx.metadata.ClassName.packageName: String
    get() {
        val split = lastIndexOf('/')
        return if (split == -1) {
            ""
        } else {
            substring(0, split).replace('/', '.')
        }
    }

val kotlinx.metadata.ClassName.simpleName: String
    get() {
        val split = lastIndexOf('/')
        return substring(split + 1)
    }

fun KmType.asTypeName(): TypeName? {
    val abbreviatedType = abbreviatedType
    if (abbreviatedType != null) {
        return abbreviatedType.asTypeName()
            ?.copy(tags = mapOf(TypeAliasTag::class to TypeAliasTag(asActualTypeName()!!)))
    }
    return asActualTypeName()
}

private fun KmType.asActualTypeName(): TypeName? {
    val isNullable = Flag.Type.IS_NULLABLE(flags)
    return if (isFunction()) {
        if (isSuspendFunction()) {
            val returnType = arguments[arguments.size - 2].type!!.arguments[0]
            val parameters = arguments.dropLast(2)
            LambdaTypeName.get(
                parameters = parameters.map { it.type!!.asTypeName()!! }.toTypedArray(),
                returnType = returnType.type!!.asTypeName()!!
            ).copy(suspending = true)
        } else {
            val returnType = arguments.last()
            val parameters = arguments.dropLast(1)
            LambdaTypeName.get(
                parameters = parameters.map { it.type!!.asTypeName()!! }.toTypedArray(),
                returnType = returnType.type!!.asTypeName()!!
            )
        }
    } else {
        val className = classifier.asClassName() ?: return null
        if (arguments.isEmpty()) {
            className
        } else {
            className.parameterizedBy(arguments.map { it.type!!.asTypeName()!! })
        }
    }.copy(nullable = isNullable)
}

private val FUNCTION = Regex("kotlin/Function[0-9]+")

fun KmType.isFunction(): Boolean {
    return classifier.name?.matches(FUNCTION) == true
}

private fun KmType.isSuspendFunction(): Boolean {
    return isFunction() &&
        arguments.size >= 2 &&
        arguments[arguments.size - 2].type?.classifier?.name == "kotlin/coroutines/Continuation"
}

fun AnnotationMirror.eqv(other: AnnotationMirror): Boolean {
    if (annotationType != other.annotationType) {
        return false
    }
    return elementValues.values.eqvItr(other.elementValues.values) { a, b -> a.value == b.value }
}

fun AnnotationMirror.eqvHashCode(): Int {
    return collectHash {
        hash(annotationType)
        for (value in elementValues.values) {
            hash(value.value)
        }
    }
}

fun KmType.eqv(other: KmType): Boolean {
    val abbreviatedType = abbreviatedType
    if (abbreviatedType != null) {
        val otherAbbreviatedType = other.abbreviatedType
        return if (otherAbbreviatedType == null) {
            false
        } else {
            abbreviatedType.eqv(otherAbbreviatedType)
        }
    }
    return classifier == other.classifier &&
        isNullable() == other.isNullable() &&
        isPlatformType() == other.isPlatformType() &&
        arguments.eqvItr(other.arguments) { a, b ->
            a.variance == b.variance &&
                a.type.eqv(b.type, KmType::eqv)
        }
}

fun KmType.eqvHashCode(collector: HashCollector = HashCollector()): Int {
    return collectHash(collector) {
        val abbreviatedType = abbreviatedType
        if (abbreviatedType != null) {
            abbreviatedType.eqvHashCode(this)
        } else {
            hash(classifier)
            for (argument in arguments) {
                hash(argument.variance)
                argument.type?.eqvHashCode(this)
            }
        }
    }
}

/**
 * Returns the [KmType] without type alias info.
 */
fun KmType.resolve(): KmType = KmType(flags).also {
    it.classifier = classifier
    it.arguments.clear()
    it.arguments.addAll(arguments)
    it.abbreviatedType = null
    it.outerType = outerType
    it.flexibleTypeUpperBound = flexibleTypeUpperBound
}

fun TypeMirror.eqvHashCode(collector: HashCollector = HashCollector()): Int = collectHash(collector) {
    if (this@eqvHashCode is DeclaredType) {
        val element = asElement()
        hash(element.simpleName)
        for (arg in typeArguments) {
            arg.eqvHashCode(this)
        }
    }
}

inline fun KmClass.isAbstract() = Flag.Common.IS_ABSTRACT(flags)

inline fun KmClass.isInterface() = Flag.Class.IS_INTERFACE(flags)

inline fun KmClass.isObject() = Flag.Class.IS_OBJECT(flags) or Flag.Class.IS_COMPANION_OBJECT(flags)

inline fun KmFunction.isAbstract() = Flag.Common.IS_ABSTRACT(flags)

inline fun KmFunction.isSuspend() = Flag.Function.IS_SUSPEND(flags)

inline fun KmProperty.isAbstract() = Flag.Common.IS_ABSTRACT(flags)

inline fun KmProperty.isPrivate() = Flag.Common.IS_PRIVATE(flags)

inline fun KmConstructor.isPrimary() = !Flag.Constructor.IS_SECONDARY(flags)

inline fun KmValueParameter.hasDefault() = Flag.ValueParameter.DECLARES_DEFAULT_VALUE(flags)

inline fun KmType.isNullable() = Flag.Type.IS_NULLABLE(flags)

inline fun KmType.isPlatformType() = flexibleTypeUpperBound?.typeFlexibilityId == "kotlin.jvm.PlatformType"