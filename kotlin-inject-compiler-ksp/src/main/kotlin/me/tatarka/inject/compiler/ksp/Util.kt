package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.symbol.AnnotationUseSiteTarget
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import me.tatarka.inject.compiler.HashCollector
import me.tatarka.inject.compiler.collectHash
import me.tatarka.inject.compiler.eqv
import me.tatarka.inject.compiler.eqvItr

fun KSAnnotated.annotationAnnotatedWith(
    className: String,
    useSiteTarget: AnnotationUseSiteTarget? = null
): KSAnnotation? {
    for (annotation in annotations) {
        if (annotation.useSiteTarget == useSiteTarget) {
            val t = annotation.annotationType.resolve()
            if (t.declaration.hasAnnotation(className)) {
                return annotation
            }
        }
    }
    return null
}

fun KSAnnotated.hasAnnotation(className: String, useSiteTarget: AnnotationUseSiteTarget? = null): Boolean {
    return annotations.any {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == className &&
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
    val declaration = resolve().declaration
    return if (declaration is KSTypeParameter) {
        val parent = declaration.parentDeclaration!!
        val resolvedParent =
            enclosingClass.superTypes.first { it.resolve().declaration.qualifiedName == parent.qualifiedName }
                .resolve()
        val typePosition = parent.typeParameters.indexOfFirst { it.name == declaration.name }
        resolvedParent.arguments[typePosition].type!!
    } else {
        this
    }
}

fun KSType.asTypeName(): TypeName {
    val isFunction = isFunction()
    val isSuspending = isSuspendingFunction()
    if (isFunction || isSuspending) {
        val returnType = arguments.last()
        val parameters = arguments.dropLast(1)
        return LambdaTypeName.get(
            parameters = parameters.map { it.type!!.resolve().asTypeName() }.toTypedArray(),
            returnType = returnType.type!!.resolve().asTypeName()
        ).let {
            if (isSuspending) {
                it.copy(suspending = true)
            } else {
                it
            }
        }
    }

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
                bounds = typeParameter.bounds.map { it.resolve().asTypeName() },
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
                typeArgumentNames += typeArgument.type!!.resolve().asTypeName()
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
    return resolve().eqv(other.resolve())
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
    resolve().eqvHashCode(collector)
}


fun KSType.isFunction(): Boolean {
    val name = declaration.qualifiedName ?: return false
    return name.getQualifier() == "kotlin" && name.getShortName().matches(Regex("Function[0-9]+"))
}

fun KSType.isSuspendingFunction(): Boolean {
    val name = declaration.qualifiedName ?: return false
    return name.getQualifier() == "kotlin.coroutines" && name.getShortName().matches(Regex("SuspendFunction[0-9]+"))
}
