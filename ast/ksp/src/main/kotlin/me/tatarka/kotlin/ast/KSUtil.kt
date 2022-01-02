package me.tatarka.kotlin.ast

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference

internal fun KSAnnotated.annotationAnnotatedWith(packageName: String, simpleName: String): KSAnnotation? {
    for (annotation in annotations) {
        val t = annotation.annotationType.resolve()
        if (t.declaration.hasAnnotation(packageName, simpleName)) {
            return annotation
        }
    }
    return null
}

internal fun KSAnnotated.hasAnnotation(packageName: String, simpleName: String): Boolean {
    return annotations.any { it.hasName(packageName, simpleName) }
}

private fun KSAnnotation.hasName(packageName: String, simpleName: String): Boolean {
    // we can skip resolving if the short name doesn't match
    if (shortName.asString() != simpleName) return false
    val declaration = annotationType.resolve().declaration
    return declaration.packageName.asString() == packageName
}

/**
 * package name except root is "" instead of "<root>"
 */
internal fun KSDeclaration.simplePackageName(): String {
    val packageName = packageName.asString()
    return if (packageName == "<root>") "" else packageName
}

internal val KSDeclaration.shortName: String
    get() {
        val name = requireNotNull(qualifiedName) { "expected qualifiedName for '$this' but got null" }
        val packageName = packageName.asString()
        return name.asString().removePrefix("$packageName.")
    }

internal fun KSAnnotation.eqv(other: KSAnnotation): Boolean {
    return annotationType.resolve() == other.annotationType.resolve() &&
            arguments == other.arguments
}

internal fun KSTypeReference.eqv(other: KSTypeReference): Boolean {
    return resolve().eqv(other.resolve())
}

internal fun KSType.eqv(other: KSType): Boolean {
    return hasSameName(other) &&
            nullability == other.nullability &&
            arguments.eqvItr(other.arguments) { a, b ->
                a.variance == b.variance && a.type.eqv(
                    b.type,
                    KSTypeReference::eqv
                )
            }
}

private fun KSType.hasSameName(other: KSType): Boolean {
    val thisName = robustName
    val otherName = other.robustName
    return if (thisName == null && otherName == null) {
        false
    } else {
        thisName == otherName
    }
}

internal fun KSType.eqvHashCode(collector: HashCollector = HashCollector()): Int = collectHash(collector) {
    hash(robustName)
    hash(nullability)
    for (argument in arguments) {
        hash(argument.variance)
        argument.type?.eqvHashCode(this)
    }
}

private val KSType.robustName: String?
    get() {
        return if (isError) {
            // Can't rely on qualified name, sometimes toString() will still give a unique result
            val thisString = toString()
            if (thisString == "<ERROR TYPE>") {
                null
            } else {
                thisString
            }
        } else {
            declaration.qualifiedName?.asString()
        }
    }

internal fun KSTypeReference.eqvHashCode(collector: HashCollector): Int = collectHash(collector) {
    resolve().eqvHashCode(collector)
}

internal fun KSType.isConcrete(): Boolean {
    if (declaration is KSTypeParameter) return false
    if (arguments.isEmpty()) return true
    return arguments.all { it.type?.resolve()?.isConcrete() ?: false }
}