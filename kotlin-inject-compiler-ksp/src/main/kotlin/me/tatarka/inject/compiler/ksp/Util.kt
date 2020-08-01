package me.tatarka.inject.compiler.ksp

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import org.jetbrains.kotlin.ksp.isAbstract
import org.jetbrains.kotlin.ksp.symbol.*
import org.jetbrains.kotlin.ksp.visitor.KSDefaultVisitor
import kotlin.reflect.KClass


fun <T : Annotation> KSAnnotated.typeAnnotatedWith(type: KClass<T>): KSType? {
    for (annotation in annotations) {
        val t = annotation.annotationType.resolve()
        val a = t?.declaration?.getAnnotation(type)
        if (a != null) {
            return t
        }
    }
    return null
}

inline fun <reified T : Annotation> KSDeclaration.typeAnnotatedWith() = typeAnnotatedWith(T::class)

fun KSAnnotated.getAnnotation(type: KClass<out Annotation>): KSAnnotation? {
    return annotations.find { it.annotationType.resolve()?.declaration?.qualifiedName?.asString() == type.qualifiedName }
}

fun KSDeclaration.asClassName(): ClassName {
    val name = qualifiedName!!
    return ClassName(name.getQualifier(), name.getShortName())
}

fun KSDeclaration.isAbstract() = when (this) {
    is KSFunctionDeclaration -> isAbstract
    is KSPropertyDeclaration -> isAbstract()
    is KSClassDeclaration -> isAbstract()
    else -> false
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
            val rawType = declaration.asClassName()
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

