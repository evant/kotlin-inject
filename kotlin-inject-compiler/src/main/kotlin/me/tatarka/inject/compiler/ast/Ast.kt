package me.tatarka.inject.compiler.ast

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.metadata.*
import kotlinx.metadata.jvm.annotations
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.signature
import me.tatarka.inject.compiler.javaToKotlinType
import me.tatarka.inject.compiler.metadata
import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.NoType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

interface AstProvider {
    val types: Types
    val elements: Elements
    val messager: Messager

    fun TypeElement.toAstClass(): AstClass? {
        val metadata = metadata ?: return null
        return AstClass(this@AstProvider, this, metadata)
    }

    fun ExecutableElement.toAstMethod(parent: AstClass): AstMethod? {
        for (property in parent.kmClass.properties) {
            val javaName = property.getterSignature?.name ?: continue
            if (simpleName.contentEquals(javaName)) {
                return AstProperty(this@AstProvider, this, property)
            }
        }
        for (function in parent.kmClass.functions) {
            val javaName = function.signature?.name
            if (simpleName.contentEquals(javaName)) {
                return AstFunction(this@AstProvider, this, function)
            }
        }
        return null
    }

    fun TypeMirror.toAstType(parent: AstFunction, enclosingClass: AstClass? = null): AstType {
        return toAstType(parent.element, parent.kmFunction.returnType, enclosingClass)
    }

    fun TypeMirror.toAstType(parent: AstProperty, enclosingClass: AstClass? = null): AstType {
        return toAstType(parent.element, parent.kmProperty.returnType, enclosingClass)
    }

    private fun TypeMirror.toAstType(element: ExecutableElement, kmType: KmType, enclosingClass: AstClass?): AstType {
        return if (enclosingClass != null) {
            val declaredType = enclosingClass.element.asType() as DeclaredType
            val methodType = types.asMemberOf(declaredType, element) as ExecutableType
            AstType(this@AstProvider, methodType.returnType, kmType)
        } else {
            AstType(this@AstProvider, element.returnType, kmType)
        }
    }
}

class AstClass(val provider: AstProvider, val element: TypeElement, val kmClass: KmClass) : AstProvider by provider {
    val superclass: AstClass? by lazy {
        val superclassType = element.superclass
        if (superclassType is NoType) return@lazy null
        val superclass = provider.types.asElement(superclassType) as TypeElement
        superclass.toAstClass()
    }

    val interfaces: List<AstClass> by lazy {
        element.interfaces.mapNotNull { ifaceType ->
            val iface = provider.types.asElement(ifaceType) as TypeElement
            iface.toAstClass()
        }
    }

    val methods: List<AstMethod> by lazy {
        ElementFilter.methodsIn(element.enclosedElements).mapNotNull { method ->
            method.toAstMethod(this)
        }
    }

    fun visitInheritanceChain(f: (AstClass) -> Unit) {
        f(this)
        superclass?.visitInheritanceChain(f)
        interfaces.forEach { it.visitInheritanceChain(f) }
    }
}


sealed class AstMethod(val element: ExecutableElement) {
    abstract val name: String
    abstract val returnType: AstType
    abstract fun returnTypeFor(enclosingClass: AstClass): AstType
}

class AstFunction(provider: AstProvider, element: ExecutableElement, val kmFunction: KmFunction) : AstMethod(element),
    AstProvider by provider {
    override val name: String get() = kmFunction.name

    override val returnType: AstType by lazy {
        element.returnType.toAstType(this)
    }

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        return element.returnType.toAstType(this, enclosingClass)
    }
}

class AstProperty(provider: AstProvider, element: ExecutableElement, val kmProperty: KmProperty) : AstMethod(element),
    AstProvider by provider {

    override val name: String get() = kmProperty.name

    override val returnType: AstType by lazy {
        element.returnType.toAstType(this)
    }

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        return element.returnType.toAstType(this, enclosingClass)
    }
}

class AstType(val provider: AstProvider, val type: TypeMirror, val kmType: KmType) {
    val annotations: List<AstAnnotation> by lazy {
        kmType.annotations.map { annotation ->
            val mirror = provider.elements.getTypeElement(annotation.className.replace('/', '.'))
            AstAnnotation(mirror.asType() as AnnotationMirror, annotation)
        }
    }

    fun asTypeName(): TypeName = type.asTypeName().javaToKotlinType()
}

class AstAnnotation(val annotationMirror: AnnotationMirror, val kmAnnotation: KmAnnotation)

private fun ExecutableElement.isProp(): Boolean =
    simpleName.startsWith("get") && ((isExtension() && parameters.size == 1) || parameters.isEmpty())

private fun ExecutableElement.isExtension() =
    parameters.isNotEmpty() && parameters[0].simpleName.startsWith("\$this")

