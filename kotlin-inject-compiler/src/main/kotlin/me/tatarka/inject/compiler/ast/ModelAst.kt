package me.tatarka.inject.compiler.ast

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ClassName
import kotlinx.metadata.*
import kotlinx.metadata.jvm.annotations
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.signature
import kotlinx.metadata.jvm.syntheticMethodForAnnotations
import me.tatarka.inject.compiler.javaToKotlinType
import me.tatarka.inject.compiler.metadata
import me.tatarka.inject.compiler.typeAnnotatedWith
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.NoType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.reflect.KClass

interface ModelAstProvider :
    AstProvider {

    val types: Types
    val elements: Elements
    val messager: Messager

    override val messenger: Messenger
        get() = ModelAstMessenger(messager)

    fun TypeElement.toAstClass(): AstClass {
        return ModelAstClass(this@ModelAstProvider, this, metadata)
    }

    override fun KClass<*>.toAstClass(): AstClass = elements.getTypeElement(java.canonicalName).toAstClass()

    override fun declaredTypeOf(klass: KClass<*>, vararg astTypes: AstType): AstType {
        val type = elements.getTypeElement(klass.java.canonicalName)
        return ModelAstType(
            this,
            declaredType(type, astTypes.asList()),
            klass.toKmType()
        )
    }

    private fun declaredType(type: TypeElement, astTypes: List<AstType>) = types.getDeclaredType(type, *astTypes.map {
        (it as ModelAstType).type
    }.toTypedArray())

    override fun TypeSpec.Builder.addOriginatingElement(astClass: AstClass): TypeSpec.Builder = apply {
        require(astClass is ModelAstClass)
        addOriginatingElement(astClass.element)
    }
}

class ModelAstMessenger(private val messager: Messager) : Messenger {
    override fun warn(message: String, element: AstElement?) {
        print(Diagnostic.Kind.WARNING, message, element)
    }

    override fun error(message: String, element: AstElement?) {
        print(Diagnostic.Kind.ERROR, message, element)
    }

    private fun print(kind: Diagnostic.Kind, message: String, element: AstElement?) {
        messager.printMessage(kind, message, (element as? ModelAstElement)?.element)
    }
}

private interface ModelAstElement : ModelAstProvider, AstAnnotated {
    val element: Element

    override fun <T : Annotation> hasAnnotation(kclass: KClass<T>): Boolean {
        return element.getAnnotation(kclass.java) != null
    }

    override fun <T : Annotation> typeAnnotatedWith(kclass: KClass<T>): AstClass? {
        return element.typeAnnotatedWith(kclass)?.toAstClass()
    }
}

private interface ModelAstMethod : ModelAstElement {
    override val element: ExecutableElement
}

private class ModelBasicElement(provider: ModelAstProvider, override val element: Element) : AstBasicElement(),
    ModelAstElement, ModelAstProvider by provider {
    override val simpleName: String get() = element.simpleName.toString()
}

private class ModelAstClass(
    provider: ModelAstProvider,
    override val element: TypeElement,
    val kmClass: KmClass?
) : AstClass(), ModelAstElement, ModelAstProvider by provider {

    override val packageName: String get() = elements.getPackageOf(element).qualifiedName.toString()

    override val name: String get() = element.simpleName.toString()

    override val modifiers: Set<AstModifier> by lazy { collectModifiers(kmClass?.flags) }

    override val companion: AstClass? by lazy {
        val companionName = kmClass?.companionObject ?: return@lazy null
        val companionType = ElementFilter.typesIn(element.enclosedElements).firstOrNull { type ->
            type.simpleName.contentEquals(companionName)
        }
        companionType?.toAstClass()
    }

    override val superTypes: List<AstClass> by lazy {
        mutableListOf<AstClass>().apply {
            val superclassType = element.superclass
            if (superclassType !is NoType) {
                val superclass = provider.types.asElement(superclassType) as TypeElement
                add(superclass.toAstClass())
            }
            addAll(element.interfaces.mapNotNull { ifaceType ->
                val iface = provider.types.asElement(ifaceType) as TypeElement
                iface.toAstClass()
            })
        }
    }

    override val primaryConstructor: AstConstructor? by lazy {
        ElementFilter.constructorsIn(element.enclosedElements).mapNotNull { constructor ->
            //TODO: not sure how to match constructors
            ModelAstConstructor(this, this, constructor, kmClass?.constructors?.first())
        }.firstOrNull()
    }

    override val methods: List<AstMethod> by lazy {
        ElementFilter.methodsIn(element.enclosedElements).mapNotNull { method ->
            if (kmClass != null) {
                for (property in kmClass.properties) {
                    val javaName = property.getterSignature?.name ?: continue
                    if (method.simpleName.contentEquals(javaName)) {
                        return@mapNotNull ModelAstProperty(this, method, property)
                    }
                }
                for (function in kmClass.functions) {
                    val javaName = function.signature?.name
                    if (method.simpleName.contentEquals(javaName)) {
                        return@mapNotNull ModelAstFunction(this, method, function)
                    }
                }
            }
            null
        }
    }

    override val type: AstType by lazy {
        ModelAstType(provider, element.asType(), kmClass?.type)
    }

    override fun asClassName(): ClassName = element.asClassName()

    override fun equals(other: Any?): Boolean = other is ModelAstElement && element == other.element

    override fun hashCode(): Int = element.hashCode()
}

private class ModelAstConstructor(
    provider: ModelAstProvider,
    parent: AstClass,
    override val element: ExecutableElement,
    private val kmConstructor: KmConstructor?
) : AstConstructor(parent), ModelAstElement, ModelAstProvider by provider {

    override val parameters: List<AstParam> by lazy {
        element.parameters.mapNotNull { element ->
            if (kmConstructor != null) {
                for (parameter in kmConstructor.valueParameters) {
                    if (element.simpleName.contentEquals(parameter.name)) {
                        return@mapNotNull ModelAstParam(this, element, parameter)
                    }
                }
            }
            null
        }
    }
}

private class ModelAstFunction(
    provider: ModelAstProvider,
    override val element: ExecutableElement,
    private val kmFunction: KmFunction
) : AstFunction(), ModelAstMethod, ModelAstProvider by provider {

    override val name: String get() = kmFunction.name

    override val modifiers: Set<AstModifier> by lazy { collectModifiers(kmFunction.flags) }

    override val returnType: AstType
        get() = ModelAstType(this, element.returnType, kmFunction.returnType)

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        require(enclosingClass is ModelAstClass)
        val declaredType = enclosingClass.element.asType() as DeclaredType
        val methodType = types.asMemberOf(declaredType, element) as ExecutableType
        return ModelAstType(this, methodType.returnType, kmFunction.returnType)
    }

    override val receiverParameterType: AstType?
        get() = kmFunction.receiverParameterType?.let {
            ModelAstType(this, element.parameters[0].asType(), it)
        }

    override val parameters: List<AstParam> by lazy {
        element.parameters.mapNotNull { element ->
            for (parameter in kmFunction.valueParameters) {
                if (element.simpleName.contentEquals(parameter.name)) {
                    return@mapNotNull ModelAstParam(this, element, parameter)
                }
            }
            null
        }
    }
}

private class ModelAstProperty(
    provider: ModelAstProvider,
    override val element: ExecutableElement,
    private val kmProperty: KmProperty
) : AstProperty(), ModelAstMethod, ModelAstProvider by provider {

    override val name: String get() = kmProperty.name

    override val modifiers: Set<AstModifier> by lazy {
        val result = mutableSetOf<AstModifier>()
        val flags = kmProperty.flags
        if (Flag.Common.IS_PRIVATE(flags)) {
            result.add(AstModifier.PRIVATE)
        }
        if (Flag.Common.IS_ABSTRACT(flags)) {
            result.add(AstModifier.ABSTRACT)
        }
        result
    }

    override val returnType: AstType
        get() = ModelAstType(this, element.returnType, kmProperty.returnType)

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        require(enclosingClass is ModelAstClass)
        val declaredType = enclosingClass.element.asType() as DeclaredType
        val methodType = types.asMemberOf(declaredType, element) as ExecutableType
        return ModelAstType(this, methodType.returnType, kmProperty.returnType)
    }

    override val receiverParameterType: AstType?
        get() = kmProperty.receiverParameterType?.let {
            ModelAstType(this, element.parameters[0].asType(), it)
        }

    private val annotatedElement: Element? by lazy {
        val javaName = kmProperty.syntheticMethodForAnnotations?.name ?: return@lazy null
        for (method in ElementFilter.methodsIn(element.enclosingElement.enclosedElements)) {
            if (method.simpleName.contentEquals(javaName)) {
                return@lazy method
            }
        }
        null
    }

    override fun <T : Annotation> hasAnnotation(kclass: KClass<T>): Boolean {
        return annotatedElement?.getAnnotation(kclass.java) != null
    }
}

private class ModelAstType(
    provider: ModelAstProvider,
    val type: TypeMirror,
    private val kmType: KmType?
) : AstType(), ModelAstElement, ModelAstProvider by provider {

    override val element: Element get() = types.asElement(type)

    override val name: String
        get() {
            return if (kmType != null) {
                when (val c = kmType.classifier) {
                    is KmClassifier.Class -> c.name.replace('/', '.')
                    is KmClassifier.TypeAlias -> c.name.replace('/', '.')
                    is KmClassifier.TypeParameter -> type.asTypeName().toString()
                }
            } else {
                type.asTypeName().toString()
            }
        }

    override val annotations: List<AstAnnotation> by lazy {
        if (kmType != null) {
            kmType.annotations.map { annotation ->
                val mirror = provider.elements.getTypeElement(annotation.className.replace('/', '.'))
                ModelAstAnnotation(this, mirror.asType() as DeclaredType, annotation)
            }
        } else {
            emptyList()
        }
    }

    override val typeAliasName: String?
        get() {
            return (kmType?.abbreviatedType?.classifier as? KmClassifier.TypeAlias)?.name?.replace('/', '.')
        }

    override val arguments: List<AstType> by lazy {
        if (kmType != null) {
            (type as DeclaredType).typeArguments.zip(kmType.arguments).map { (a1, a2) ->
                ModelAstType(this, a1, a2.type!!)
            }
        } else {
            emptyList()
        }
    }

    override fun isUnit(): Boolean = type is NoType

    override fun asElement(): AstBasicElement = ModelBasicElement(this, element)

    override fun toAstClass(): AstClass = (element as TypeElement).toAstClass()

    override fun asTypeName(): TypeName {
        return typeAliasName?.let { ClassName.bestGuess(it) } ?: type.asTypeName()
            .javaToKotlinType()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AstType) return false
        return asTypeName() == other.asTypeName()
    }

    override fun hashCode(): Int {
        return asTypeName().hashCode()
    }

    override fun toString(): String {
        val n = name
        return if (n.substringBeforeLast('.') == "kotlin") n.removePrefix("kotlin.") else n
    }
}

private class ModelAstAnnotation(
    provider: ModelAstProvider,
    val annotationType: DeclaredType,
    private val kmAnnotation: KmAnnotation
) : AstAnnotation(), ModelAstElement, ModelAstProvider by provider {
    override val element: Element get() = types.asElement(annotationType)

    override fun equals(other: Any?): Boolean {
        if (other !is ModelAstAnnotation) return false
        return kmAnnotation == other.kmAnnotation
    }

    override fun hashCode(): Int {
        return kmAnnotation.hashCode()
    }

    override fun toString(): String {
        return "@$annotationType(${kmAnnotation.arguments.toList()
            .joinToString(separator = ", ") { (name, value) -> "$name=${value.value}" }})"
    }
}

private class ModelAstParam(
    provider: ModelAstProvider,
    override val element: VariableElement,
    val kmValueParameter: KmValueParameter
) : AstParam(), ModelAstElement, ModelAstProvider by provider {

    override val name: String get() = kmValueParameter.name

    override val type: AstType by lazy {
        ModelAstType(this, element.asType(), kmValueParameter.type!!)
    }

    override fun asParameterSpec(): ParameterSpec = ParameterSpec.get(element)
}

private val KmClass.type: KmType
    get() = KmType(flags = flags).apply {
        classifier = KmClassifier.Class(name)
    }

private val KmAnnotation.type: KmType
    get() = KmType(0).apply {
        classifier = KmClassifier.Class(className)
    }

private fun KClass<*>.toKmType(): KmType = KmType(0).apply {
    classifier = KmClassifier.Class(java.canonicalName)
}

private fun collectModifiers(flags: Flags?): Set<AstModifier> {
    val result = mutableSetOf<AstModifier>()
    if (flags == null) return result
    if (Flag.Common.IS_PRIVATE(flags)) {
        result.add(AstModifier.PRIVATE)
    }
    if (Flag.Common.IS_ABSTRACT(flags)) {
        result.add(AstModifier.ABSTRACT)
    }
    return result
}

