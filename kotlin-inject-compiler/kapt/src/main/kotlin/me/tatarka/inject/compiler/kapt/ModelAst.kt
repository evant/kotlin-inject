package me.tatarka.inject.compiler.kapt

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.KmVariance
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.signature
import me.tatarka.inject.compiler.AstAnnotated
import me.tatarka.inject.compiler.AstAnnotation
import me.tatarka.inject.compiler.AstBasicElement
import me.tatarka.inject.compiler.AstClass
import me.tatarka.inject.compiler.AstConstructor
import me.tatarka.inject.compiler.AstElement
import me.tatarka.inject.compiler.AstFunction
import me.tatarka.inject.compiler.AstMethod
import me.tatarka.inject.compiler.AstParam
import me.tatarka.inject.compiler.AstProperty
import me.tatarka.inject.compiler.AstProvider
import me.tatarka.inject.compiler.AstType
import me.tatarka.inject.compiler.Messenger
import me.tatarka.inject.compiler.OutputProvider
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.NoType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.reflect.KClass

interface ModelAstProvider : AstProvider {

    val env: ProcessingEnvironment

    val types: Types get() = env.typeUtils
    val elements: Elements get() = env.elementUtils

    override val messenger: Messenger
        get() = ModelAstMessenger(env.messager)

    fun TypeElement.toAstClass(): AstClass {
        return ModelAstClass(this@ModelAstProvider, this)
    }

    @Suppress("LoopWithTooManyJumpStatements")
    override fun findFunctions(packageName: String, functionName: String): Sequence<AstFunction> {
        val packageElement = elements.getPackageElement(packageName)
        val results = mutableListOf<AstFunction>()
        for (element in ElementFilter.typesIn(packageElement.enclosedElements)) {
            for (function in ElementFilter.methodsIn(element.enclosedElements)) {
                if (function.simpleName.contentEquals(functionName) &&
                    Modifier.STATIC in function.modifiers &&
                    Modifier.FINAL in function.modifiers
                ) {
                    val metadata = element.metadata?.toKmPackage() ?: continue
                    val kmFunction = metadata.functions.find { it.name == functionName } ?: continue
                    results.add(ModelAstFunction(this, element.toAstClass() as ModelAstClass, function, kmFunction))
                }
            }
        }
        return results.asSequence()
    }

    override fun declaredTypeOf(klass: KClass<*>, vararg astTypes: AstType): AstType {
        val type = elements.getTypeElement(klass.java.canonicalName)
        return ModelAstType(
            this,
            elementDeclaredType(type, astTypes.asList()),
            kmDeclaredType(klass.kmClassName(), astTypes.asList())
        )
    }

    private fun kmDeclaredType(name: kotlinx.metadata.ClassName, astTypes: List<AstType>): KmType = KmType(0).apply {
        classifier = KmClassifier.Class(name).apply {
            arguments.addAll(
                astTypes.map { type ->
                    require(type is ModelAstType)
                    KmTypeProjection(KmVariance.INVARIANT, type.kmType)
                }
            )
        }
    }

    private fun KClass<*>.kmClassName(): kotlinx.metadata.ClassName {
        val qualifiedName: String
        val simpleName: String
        if (this.qualifiedName != null) {
            qualifiedName = this.qualifiedName!!
            simpleName = this.simpleName!!
        } else {
            qualifiedName = java.canonicalName
            simpleName = java.simpleName
        }
        val packageName = qualifiedName.removeSuffix(".$simpleName").replace('.', '/')
        return "$packageName/$simpleName"
    }

    private fun ModelAstType.kmClassName(): kotlinx.metadata.ClassName {
        return when (val classifier = kmType?.classifier) {
            is KmClassifier.Class -> classifier.name
            is KmClassifier.TypeAlias -> classifier.name
            is KmClassifier.TypeParameter -> ""
            null -> element.toString()
        }
    }

    private fun elementDeclaredType(type: TypeElement, astTypes: List<AstType>) =
        types.getDeclaredType(
            type,
            *astTypes.map {
                (it as ModelAstType).type
            }.toTypedArray()
        )

    override fun validate(element: AstClass): Boolean {
        // TODO: kapt validation?
        return true
    }

    override fun AstElement.toTrace(): String {
        return when (this) {
            is ModelAstMethod -> "${parent.type}.${toString()}"
            else -> toString()
        }
    }
}

interface ModelOutputProvider : OutputProvider {

    override fun buildTypeSpec(typeSpecBuilder: TypeSpec.Builder, originatingElement: AstClass): TypeSpec {
        require(originatingElement is ModelAstClass)
        return typeSpecBuilder
            .addOriginatingElement(originatingElement.element)
            .build()
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

private interface ModelAstElement {
    val element: Element
}

private interface ModelAstAnnotated : ModelAstElement, AstAnnotated {
    val provider: ModelAstProvider

    override fun hasAnnotation(packageName: String, simpleName: String): Boolean {
        return element.hasAnnotation(packageName, simpleName)
    }

    override fun annotationAnnotatedWith(packageName: String, simpleName: String): AstAnnotation? {
        return element.annotationAnnotatedWith(packageName, simpleName)?.let {
            ModelAstAnnotation(provider, it, null)
        }
    }
}

private interface ModelAstMethod : ModelAstAnnotated {
    val name: String
    val parent: AstClass
    override val element: ExecutableElement
}

private class ModelBasicElement(override val element: Element) : AstBasicElement(), ModelAstElement {
    override val simpleName: String get() = element.simpleName.toString()
}

private class PrimitiveModelAstClass(
    override val type: ModelAstType
) : AstClass() {

    override val packageName: String = "kotlin"
    override val name: String = type.toString()
    override val visibility: KModifier = KModifier.PUBLIC
    override val isAbstract: Boolean = false
    override val isInterface: Boolean = false
    override val companion: AstClass? = null
    override val isObject: Boolean = false
    override val superTypes: Sequence<AstClass> = emptySequence()
    override val primaryConstructor: AstConstructor? = null
    override val constructors: Sequence<AstConstructor> = emptySequence()
    override val methods: List<AstMethod> = emptyList()

    override fun asClassName(): ClassName = throw UnsupportedOperationException()

    override fun equals(other: Any?): Boolean {
        return other is PrimitiveModelAstClass && type == other.type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    override fun hasAnnotation(packageName: String, simpleName: String): Boolean = false
    override fun annotationAnnotatedWith(packageName: String, simpleName: String): AstAnnotation? = null
}

private class ModelAstClass(
    override val provider: ModelAstProvider,
    override val element: TypeElement,
) : AstClass(), ModelAstAnnotated {

    val kmClass: KmClass? = element.metadata?.toKmClass()

    override val packageName: String
        get() {
            if (kmClass != null) {
                return kmClass.packageName
            }
            return provider.elements.getPackageOf(element).qualifiedName.toString()
        }

    override val name: String get() = element.simpleName.toString()

    override val visibility: KModifier
        get() = toKModifier(element, kmClass?.flags)

    override val isAbstract: Boolean
        get() = kmClass?.isAbstract() ?: false

    override val isInterface: Boolean
        get() = kmClass?.isInterface() ?: false

    override val isObject: Boolean
        get() = kmClass?.isObject() ?: false

    override val companion: AstClass?
        get() {
            val companionName = kmClass?.companionObject ?: return null
            val companionType = ElementFilter.typesIn(element.enclosedElements).firstOrNull { type ->
                type.simpleName.contentEquals(companionName)
            }
            return companionType?.let { ModelAstClass(provider, it) }
        }

    override val superTypes: Sequence<AstClass>
        get() {
            return sequence {
                val superclassType = element.superclass
                if (superclassType !is NoType) {
                    val superclass = provider.types.asElement(superclassType) as TypeElement
                    yield(ModelAstClass(provider, superclass))
                }
                for (ifaceType in element.interfaces) {
                    val iface = provider.types.asElement(ifaceType) as TypeElement
                    yield(ModelAstClass(provider, iface))
                }
            }
        }

    override val primaryConstructor: AstConstructor?
        get() {
            if (kmClass == null || isObject) return null

            val primaryKmCtor = kmClass.constructors.find(KmConstructor::isPrimary)
            val primaryKmCtorSignature = primaryKmCtor?.signature?.simpleSig ?: return null

            return ElementFilter.constructorsIn(element.enclosedElements)
                .find { it.simpleSig == primaryKmCtorSignature }
                ?.let { ModelAstConstructor(provider, this, it, primaryKmCtor) }
        }

    override val constructors: Sequence<AstConstructor>
        get() {
            val kmCtors = kmClass?.constructors?.associateBy { it.signature?.simpleSig } ?: emptyMap()
            return ElementFilter.constructorsIn(element.enclosedElements).asSequence().map { constructor ->
                ModelAstConstructor(
                    provider,
                    this,
                    constructor,
                    kmCtors[constructor.simpleSig]
                )
            }
        }

    override val methods: List<AstMethod>
        get() {
            if (kmClass == null) return emptyList()

            val methods = mutableMapOf<String, ExecutableElement>()

            for (method in ElementFilter.methodsIn(element.enclosedElements)) {
                methods[method.simpleSig] = method
            }

            val result = mutableListOf<AstMethod>()
            for (property in kmClass.properties) {
                val method = methods[property.getterSignature?.simpleSig] ?: continue
                result.add(
                    ModelAstProperty(
                        provider,
                        this,
                        method,
                        property
                    )
                )
            }
            for (function in kmClass.functions) {
                val method = methods[function.signature?.simpleSig] ?: continue
                result.add(
                    ModelAstFunction(
                        provider,
                        this,
                        method,
                        function
                    )
                )
            }

            return result
        }

    override val type: AstType
        get() {
            return ModelAstType(provider, element.asType(), kmClass?.type)
        }

    override fun asClassName(): ClassName = element.asClassName()

    override fun equals(other: Any?): Boolean = other is ModelAstAnnotated && element == other.element
    override fun hashCode(): Int = element.hashCode()
}

private class ModelAstConstructor(
    override val provider: ModelAstProvider,
    private val parent: ModelAstClass,
    override val element: ExecutableElement,
    private val kmConstructor: KmConstructor?
) : AstConstructor(parent), ModelAstAnnotated {

    override val parameters: List<AstParam>
        get() {
            val params = element.parameters
            val kmParams: List<KmValueParameter> = kmConstructor?.valueParameters ?: emptyList()
            return params.mapIndexed { index, param ->
                val kmParam = kmParams.getOrNull(index)
                ModelAstParam(
                    provider,
                    param,
                    parent.kmClass,
                    kmParam
                )
            }
        }

    override fun equals(other: Any?): Boolean {
        return other is ModelAstConstructor && element == other.element
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }
}

private class ModelAstFunction(
    override val provider: ModelAstProvider,
    override val parent: AstClass,
    override val element: ExecutableElement,
    private val kmFunction: KmFunction
) : AstFunction(), ModelAstMethod {

    override val name: String get() = kmFunction.name

    override val visibility: KModifier
        get() = toKModifier(element, kmFunction.flags)

    override val isAbstract: Boolean
        get() = kmFunction.isAbstract()

    override val isSuspend: Boolean
        get() = kmFunction.isSuspend()

    override val returnType: AstType
        get() = ModelAstType(
            provider,
            element.returnType,
            kmFunction.returnType
        )

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        require(enclosingClass is ModelAstClass)
        val declaredType = enclosingClass.element.asType() as DeclaredType
        val methodType = provider.types.asMemberOf(declaredType, element) as ExecutableType
        return ModelAstType(
            provider,
            methodType.returnType,
            kmFunction.returnType
        )
    }

    override val receiverParameterType: AstType?
        get() = kmFunction.receiverParameterType?.let {
            ModelAstType(provider, element.parameters[0].asType(), it)
        }

    override val parameters: List<AstParam>
        get() {
            val params = when {
                kmFunction.receiverParameterType != null -> {
                    // drop the extension function receiver if present
                    element.parameters.drop(1)
                }
                kmFunction.isSuspend() -> {
                    // drop last continuation parameter
                    element.parameters.dropLast(1)
                }
                else -> element.parameters
            }
            val kmParams: List<KmValueParameter> = kmFunction.valueParameters
            return params.mapIndexed { index, param ->
                val kmParam = kmParams.getOrNull(index)
                ModelAstParam(
                    provider,
                    param,
                    null,
                    kmParam
                )
            }
        }

    override fun overrides(other: AstMethod): Boolean {
        require(other is ModelAstMethod)
        return provider.elements.overrides(element, other.element, parent.element)
    }

    override fun asMemberName(): MemberName {
        return MemberName(provider.elements.getPackageOf(element).qualifiedName.toString(), name)
    }

    override fun equals(other: Any?): Boolean {
        return other is ModelAstFunction && element == other.element
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }
}

private class ModelAstProperty(
    override val provider: ModelAstProvider,
    override val parent: AstClass,
    override val element: ExecutableElement,
    private val kmProperty: KmProperty
) : AstProperty(), ModelAstMethod {

    override val name: String get() = kmProperty.name

    override val visibility: KModifier
        get() = toKModifier(element, kmProperty.flags)

    override val isAbstract: Boolean
        get() = kmProperty.isAbstract()

    override val returnType: AstType
        get() = ModelAstType(
            provider,
            element.returnType,
            kmProperty.returnType
        )

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        require(enclosingClass is ModelAstClass)
        val declaredType = enclosingClass.element.asType() as DeclaredType
        val methodType = provider.types.asMemberOf(declaredType, element) as ExecutableType
        return ModelAstType(
            provider,
            methodType.returnType,
            kmProperty.returnType
        )
    }

    override val receiverParameterType: AstType?
        get() = kmProperty.receiverParameterType?.let {
            ModelAstType(provider, element.parameters[0].asType(), it)
        }

    override fun overrides(other: AstMethod): Boolean {
        require(other is ModelAstMethod)
        return provider.elements.overrides(element, other.element, parent.element)
    }

    override fun asMemberName(): MemberName {
        return MemberName(provider.elements.getPackageOf(element).qualifiedName.toString(), name)
    }

    override fun equals(other: Any?): Boolean {
        return other is ModelAstProperty && element == other.element
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }
}

private class ModelAstType(
    private val provider: ModelAstProvider,
    val type: TypeMirror,
    val kmType: KmType?
) : AstType(), ModelAstElement {

    override val element: Element by lazy { provider.types.asElement(type) }

    override val packageName: String
        get() {
            if (kmType != null) {
                return kmType.packageName
            }
            return provider.elements.getPackageOf(element).qualifiedName.toString()
        }

    override val simpleName: String
        get() {
            if (kmType != null) {
                return kmType.simpleName
            }
            return element.simpleName.toString()
        }

    override val arguments: List<AstType>
        get() {
            val kmArgs: List<KmType?> = kmType?.arguments?.map { it.type } ?: emptyList()
            val args: List<TypeMirror> = (type as DeclaredType).typeArguments
            return if (args.size == kmArgs.size) {
                args.zip(kmArgs) { a1, a2 -> ModelAstType(provider, a1, a2) }
            } else {
                args.map { ModelAstType(provider, it, null) }
            }
        }

    override val isError: Boolean
        get() = type.kind == TypeKind.ERROR

    override fun isUnit(): Boolean = type is NoType

    override fun isPlatform(): Boolean = kmType?.isPlatformType() ?: false

    override fun isFunction(): Boolean {
        return kmType?.isFunction() == true
    }

    override fun isTypeAlias(): Boolean {
        return kmType?.abbreviatedType != null
    }

    override fun resolvedType(): AstType {
        val abbreviatedType = kmType?.abbreviatedType
        return if (abbreviatedType != null) {
            ModelAstType(provider, type, kmType?.resolve())
        } else {
            this
        }
    }

    override fun asElement(): AstBasicElement = ModelBasicElement(element)

    override fun toAstClass(): AstClass {
        return when (type) {
            is PrimitiveType -> PrimitiveModelAstClass(this)
            is ArrayType -> PrimitiveModelAstClass(this)
            is DeclaredType, is TypeVariable -> ModelAstClass(provider, provider.types.asElement(type) as TypeElement)
            else -> kotlin.error("unknown type: $type")
        }
    }

    override fun asTypeName(): TypeName {
        return type.asTypeName(kmType)
    }

    override fun isAssignableFrom(other: AstType): Boolean {
        require(other is ModelAstType)
        return provider.types.isAssignable(other.type, type)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ModelAstType) return false
        return if (kmType != null && other.kmType != null) {
            kmType.eqv(other.kmType)
        } else {
            provider.types.isSameType(type, other.type)
        }
    }

    override fun hashCode(): Int {
        return kmType?.eqvHashCode() ?: type.eqvHashCode()
    }

    override fun toString(): String {
        return type.asTypeName(kmType).toString().shortenPackage()
    }
}

private class ModelAstAnnotation(
    private val provider: ModelAstProvider,
    val mirror: AnnotationMirror,
    private val kmAnnotation: KmAnnotation?
) : AstAnnotation(), ModelAstElement {

    override val element: Element get() = provider.types.asElement(mirror.annotationType)

    override val type: AstType
        get() = ModelAstType(provider, mirror.annotationType, kmAnnotation?.type)

    override fun equals(other: Any?): Boolean {
        if (other !is ModelAstAnnotation) return false
        return if (kmAnnotation != null && other.kmAnnotation != null) {
            kmAnnotation == other.kmAnnotation
        } else {
            mirror.eqv(other.mirror)
        }
    }

    override fun hashCode(): Int {
        return kmAnnotation?.hashCode() ?: mirror.eqvHashCode()
    }

    override fun toString(): String {
        return "@${mirror.annotationType}(${
            if (kmAnnotation != null) {
                kmAnnotation.arguments.toList()
                    .joinToString(separator = ", ") { (name, value) -> "$name=${value.value}" }
            } else {
                mirror.elementValues.toList()
                    .joinToString(separator = ", ") { (element, value) -> "${element.simpleName}=${value.value}" }
            }
        })"
    }
}

private class ModelAstParam(
    override val provider: ModelAstProvider,
    override val element: VariableElement,
    val kmParent: KmClass?,
    val kmValueParameter: KmValueParameter?
) : AstParam(), ModelAstAnnotated {

    override val name: String
        get() {
            return kmValueParameter?.name ?: element.simpleName.toString()
        }

    override val type: AstType
        get() = ModelAstType(provider, element.asType(), kmValueParameter?.type)

    override val isVal: Boolean
        get() {
            val param = kmValueParameter ?: return false
            val parent = kmParent ?: return false
            return parent.properties.any { it.name == param.name }
        }

    override val isPrivate: Boolean
        get() {
            val param = kmValueParameter ?: return false
            val parent = kmParent ?: return false
            return parent.properties.find { it.name == param.name }?.isPrivate() ?: false
        }

    override val hasDefault: Boolean
        get() = kmValueParameter?.hasDefault() ?: false

    override fun asParameterSpec(): ParameterSpec {
        return ParameterSpec(name, type.asTypeName())
    }
}

private val KmClass.type: KmType
    get() = KmType(0).apply {
        classifier = KmClassifier.Class(name)
    }

private val KmAnnotation.type: KmType
    get() = KmType(0).apply {
        classifier = KmClassifier.Class(className)
    }

private fun KClass<*>.toKmType(args: List<KmTypeProjection>): KmType =
    (qualifiedName ?: java.canonicalName).toKmType(args)

private fun String.toKmType(args: List<KmTypeProjection>): KmType = KmType(0).apply {
    classifier = KmClassifier.Class(this@toKmType).apply {
        arguments.addAll(args)
    }
}

private fun toKModifier(element: Element, flags: Flags?): KModifier {
    return if (flags != null) {
        when {
            Flag.Common.IS_INTERNAL(flags) -> KModifier.INTERNAL
            Flag.Common.IS_PRIVATE(flags) -> KModifier.PRIVATE
            Flag.Common.IS_PROTECTED(flags) -> KModifier.PROTECTED
            else -> KModifier.PUBLIC
        }
    } else {
        val modifiers = element.modifiers
        when {
            Modifier.PROTECTED in modifiers -> KModifier.PROTECTED
            Modifier.PRIVATE in modifiers -> KModifier.PRIVATE
            else -> KModifier.PUBLIC
        }
    }
}

val AstClass.element: TypeElement
    get() {
        require(this is ModelAstClass)
        return element
    }