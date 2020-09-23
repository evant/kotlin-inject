package me.tatarka.inject.compiler.ksp

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import me.tatarka.inject.compiler.HashCollector
import me.tatarka.inject.compiler.collectHash
import me.tatarka.inject.compiler.eqv
import me.tatarka.inject.compiler.eqvItr
import org.jetbrains.kotlin.ksp.isAbstract
import org.jetbrains.kotlin.ksp.symbol.AnnotationUseSiteTarget
import org.jetbrains.kotlin.ksp.symbol.KSAnnotated
import org.jetbrains.kotlin.ksp.symbol.KSAnnotation
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSFunctionDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSNode
import org.jetbrains.kotlin.ksp.symbol.KSPropertyDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSType
import org.jetbrains.kotlin.ksp.symbol.KSTypeAlias
import org.jetbrains.kotlin.ksp.symbol.KSTypeParameter
import org.jetbrains.kotlin.ksp.symbol.KSTypeReference
import org.jetbrains.kotlin.ksp.symbol.Nullability
import org.jetbrains.kotlin.ksp.symbol.Variance
import org.jetbrains.kotlin.ksp.visitor.KSDefaultVisitor

fun KSAnnotated.annotationAnnotatedWith(
    className: String,
    useSiteTarget: AnnotationUseSiteTarget? = null
): KSAnnotation? {
    for (annotation in annotations) {
        if (annotation.useSiteTarget == useSiteTarget) {
            val t = annotation.annotationType.resolve()
            if (t?.declaration?.hasAnnotation(className) == true) {
                return annotation
            }
        }
    }
    return null
}

fun KSAnnotated.hasAnnotation(className: String, useSiteTarget: AnnotationUseSiteTarget? = null): Boolean {
    return annotations.any {
        it.annotationType.resolve()?.declaration?.qualifiedName?.asString() == className &&
                useSiteTarget == it.useSiteTarget
    }
}

fun KSDeclaration.asClassName(): ClassName {
    val name = qualifiedName!!
    val packageName = packageName.asString()
    val shortName = name.asString().removePrefix("$packageName.")
    return ClassName(if (packageName == "<root>") "" else packageName, shortName.split('.'))
}

fun KSTypeReference.memberOf(enclosingClass: KSClassDeclaration): KSTypeReference {
    val declaration = resolve()!!.declaration
    return if (declaration is KSTypeParameter) {
        val parent = declaration.parentDeclaration!!
        val resolvedParent =
            enclosingClass.superTypes.first { it.resolve()!!.declaration.qualifiedName == parent.qualifiedName }
                .resolve()!!
        val typePosition = parent.typeParameters.indexOfFirst { it.name == declaration.name }
        resolvedParent.arguments[typePosition].type!!
    } else {
        this
    }
}

fun KSType.asTypeName(): TypeName {

    return declaration.accept(object : KSDefaultVisitor<Unit, TypeName>() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit): TypeName {
            return fromDeclaration(classDeclaration)
        }

        override fun visitTypeAlias(typeAlias: KSTypeAlias, data: Unit): TypeName {
            return fromDeclaration(typeAlias)
        }

        override fun visitTypeParameter(typeParameter: KSTypeParameter, data: Unit): TypeName {
            return TypeVariableName(
                name = typeParameter.name.asString(),
                bounds = typeParameter.bounds.map { it.resolve()!!.asTypeName() },
                variance = when (typeParameter.variance) {
                    Variance.COVARIANT -> KModifier.IN
                    Variance.CONTRAVARIANT -> KModifier.OUT
                    else -> null
                }
            )
        }

        private fun fromDeclaration(declaration: KSDeclaration): TypeName {
            val rawType = declaration.asClassName().copy(nullable = nullability == Nullability.NULLABLE) as ClassName
            if (declaration.typeParameters.isEmpty()) {
                return rawType
            }
            val typeArgumentNames = mutableListOf<TypeName>()
            for (typeArgument in arguments) {
                typeArgumentNames += typeArgument.type!!.resolve()!!.asTypeName()
            }
            return rawType.parameterizedBy(typeArgumentNames)
        }

        override fun defaultHandler(node: KSNode, data: Unit): TypeName {
            throw IllegalArgumentException("Unexpected node: $node")
        }
    }, Unit)
}

fun KSAnnotation.eqv(other: KSAnnotation): Boolean {
    return annotationType.resolve() == other.annotationType.resolve() &&
            arguments == other.arguments
}

fun KSTypeReference.eqv(other: KSTypeReference): Boolean {
    val t1 = resolve()
    val t2 = other.resolve()
    return if (t1 != null && t2 != null) {
        t1.eqv(t2)
    } else {
        t1 == null && t2 == null
    }
}

fun KSType.eqv(other: KSType): Boolean {
    return declaration.qualifiedName == other.declaration.qualifiedName &&
            nullability == other.nullability &&
            arguments.eqvItr(other.arguments) { a, b ->
                a.variance == b.variance && a.type.eqv(
                    b.type,
                    KSTypeReference::eqv
                )
            }
}

fun KSType.eqvHashCode(collector: HashCollector = HashCollector()): Int = collectHash(collector) {
    hash(declaration.qualifiedName)
    hash(nullability)
    for (argument in arguments) {
        hash(argument.variance)
        argument.type?.eqvHashCode(this)
    }
}

fun KSTypeReference.eqvHashCode(collector: HashCollector): Int = collectHash(collector) {
    resolve()?.eqvHashCode(collector)
}
