package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
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

fun KSAnnotated.annotationAnnotatedWith(packageName: String, simpleName: String): KSAnnotation? {
    for (annotation in annotations) {
        val t = annotation.annotationType.resolve()
        if (t.declaration.hasAnnotation(packageName, simpleName)) {
            return annotation
        }
    }
    return null
}

fun KSAnnotated.hasAnnotation(packageName: String, simpleName: String, logger: KSPLogger? = null): Boolean {
    return annotations.any { it.hasName(packageName, simpleName, logger) }
}

private fun KSAnnotation.hasName(packageName: String, simpleName: String, logger: KSPLogger? = null): Boolean {
    // we can skip resolving if the short name doesn't match
    if (shortName.asString() != simpleName) return false
    val type = annotationType.resolve()
    if (type.isError) {
        // handling for KMP common code generation: https://github.com/google/ksp/issues/632
        logger?.warn("Failed to resolve annotation @$simpleName so it's package cannot be checked." +
                " This may cause issues if you are using annotation with the same name in a different package.")
        return true
    }
    val declaration = type.declaration
    return declaration.packageName.asString() == packageName
}

/**
 * package name except root is "" instead of "<root>"
 */
fun KSDeclaration.simplePackageName(): String {
    val packageName = packageName.asString()
    return if (packageName == "<root>") "" else packageName
}

fun KSDeclaration.asClassName(): ClassName {
    val packageName = simplePackageName()
    return ClassName(packageName, shortName.split('.'))
}

val KSDeclaration.shortName: String
    get() {
        val name = requireNotNull(qualifiedName) { "expected qualifiedName for '$this' but got null" }
        val packageName = packageName.asString()
        return name.asString().removePrefix("$packageName.")
    }

fun KSType.asTypeName(): TypeName {
    if (isError) {
        throw IllegalArgumentException("Cannot convert error type: $this to a TypeName")
    }
    val isFunction = isFunctionType
    val isSuspending = isSuspendFunctionType
    if ((isFunction || isSuspending) && declaration !is KSTypeAlias) {
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

    return declaration.accept(
        object : KSDefaultVisitor<Unit, TypeName>() {

            override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit): TypeName {
                return fromDeclaration(classDeclaration)
            }

            override fun visitTypeAlias(typeAlias: KSTypeAlias, data: Unit): TypeName {
                return fromDeclaration(typeAlias)
            }

            override fun visitTypeParameter(typeParameter: KSTypeParameter, data: Unit): TypeName {
                return TypeVariableName(
                    name = typeParameter.name.asString(),
                    bounds = typeParameter.bounds.map { it.resolve().asTypeName() }.toList(),
                    variance = when (typeParameter.variance) {
                        Variance.COVARIANT -> KModifier.IN
                        Variance.CONTRAVARIANT -> KModifier.OUT
                        else -> null
                    }
                )
            }

            private fun fromDeclaration(declaration: KSDeclaration): TypeName {
                val rawType =
                    declaration.asClassName().copy(nullable = nullability == Nullability.NULLABLE) as ClassName
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
        },
        Unit
    )
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

fun KSType.isConcrete(): Boolean {
    if (declaration is KSTypeParameter) return false
    if (arguments.isEmpty()) return true
    return arguments.all { it.type?.resolve()?.isConcrete() ?: false }
}

/**
 * A 'fast' version of [Resolver.getSymbolsWithAnnotation]. We only care about class annotations so we can skip a lot
 * of the tree.
 */
fun Resolver.getSymbolsWithClassAnnotation(
    packageName: String,
    simpleName: String,
    logger: KSPLogger? = null,
): Sequence<KSClassDeclaration> {
    suspend fun SequenceScope<KSClassDeclaration>.visit(declarations: Sequence<KSDeclaration>) {
        for (declaration in declarations) {
            if (declaration is KSClassDeclaration) {
                if (declaration.hasAnnotation(packageName, simpleName, logger)) {
                    yield(declaration)
                }
                visit(declaration.declarations)
            }
        }
    }
    return sequence {
        for (file in getNewFiles()) {
            visit(file.declarations)
        }
    }
}