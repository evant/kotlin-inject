package me.tatarka.inject.compiler.kapt

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ClassName
import kotlinx.metadata.*
import kotlinx.metadata.jvm.annotations
import kotlinx.metadata.jvm.syntheticMethodForAnnotations
import me.tatarka.inject.compiler.*
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.*
import javax.lang.model.type.*
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.reflect.KClass

interface ModelAstProvider :
    AstProvider {

    val env: ProcessingEnvironment

    val types: Types get() = env.typeUtils
    val elements: Elements get() = env.elementUtils
    val messager: Messager get() = env.messager

    override val messenger: Messenger
        get() = ModelAstMessenger(messager)

    fun TypeElement.toAstClass(): AstClass {
        return ModelAstClass(this@ModelAstProvider, this, metadata?.toKmClass())
    }

    override fun findFunctions(packageName: String, functionName: String): List<AstFunction> {
        val packageElement = elements.getPackageElement(packageName)
        val results = mutableListOf<AstFunction>()
        for (element in ElementFilter.typesIn(packageElement.enclosedElements)) {
            for (function in ElementFilter.methodsIn(element.enclosedElements)) {
                if (function.simpleName.contentEquals(functionName)
                    && function.modifiers.contains(Modifier.STATIC) && function.modifiers.contains(Modifier.STATIC) && function.modifiers.contains(
                        Modifier.FINAL
                    )
                ) {
                    val metadata = element.metadata?.toKmPackage() ?: continue
                    val kmFunction = metadata.functions.find { it.name == functionName } ?: continue
                    results.add(ModelAstFunction(this, element.toAstClass(), function, kmFunction))
                }
            }
        }
        return results
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
            arguments.addAll(astTypes.map { type ->
                require(type is ModelAstType)
                KmTypeProjection(KmVariance.INVARIANT, type.kmType)
            })
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
        val packageName = qualifiedName.removeSuffix(simpleName).replace('.', '/')
        return "${packageName}/${simpleName}"
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
        types.getDeclaredType(type, *astTypes.map {
            (it as ModelAstType).type
        }.toTypedArray())

    override fun AstElement.toTrace(): String {
        require(this is ModelAstElement)
        return when (this) {
            is ModelAstMethod -> "${parent.type}.${toString()}"
            else -> toString()
        }
    }

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

    override fun hasAnnotation(className: String): Boolean {
        return element.hasAnnotation(className)
    }

    override fun typeAnnotatedWith(className: String): AstClass? {
        return element.typeAnnotatedWith(className)?.toAstClass()
    }
}

private interface ModelAstMethod : ModelAstElement {
    val name: String
    val parent: AstClass
    override val element: ExecutableElement
}

private class ModelBasicElement(provider: ModelAstProvider, override val element: Element) : AstBasicElement(),
    ModelAstElement, ModelAstProvider by provider {
    override val simpleName: String get() = element.simpleName.toString()
}

private class PrimitiveModelAstClass(
    override val type: ModelAstType
) : AstClass(), ModelAstProvider by type {
    override val packageName: String = "kotlin"
    override val name: String = type.toString()
    override val modifiers: Set<AstModifier> = emptySet()
    override val companion: AstClass? = null
    override val superTypes: List<AstClass> = emptyList()
    override val primaryConstructor: AstConstructor? = null
    override val methods: List<AstMethod> = emptyList()

    override fun asClassName(): ClassName = throw UnsupportedOperationException()

    override fun equals(other: Any?): Boolean {
        return other is PrimitiveModelAstClass && type == other.type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    override fun hasAnnotation(className: String): Boolean = false
    override fun typeAnnotatedWith(className: String): AstClass? = null
}

private class ModelAstClass(
    provider: ModelAstProvider,
    override val element: TypeElement,
    val kmClass: KmClass?
) : AstClass(),
    ModelAstElement, ModelAstProvider by provider {

    override val packageName: String
        get() {
            if (kmClass != null) {
                return kmClass.packageName
            }
            return elements.getPackageOf(element).qualifiedName.toString()
        }

    override val name: String get() = element.simpleName.toString()

    override val modifiers: Set<AstModifier> by lazy {
        collectModifiers(
            kmClass?.flags
        )
    }

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
            ModelAstConstructor(
                this,
                this,
                constructor,
                kmClass?.constructors?.first()
            )
        }.firstOrNull()
    }

    override val methods: List<AstMethod> by lazy {
        ElementFilter.methodsIn(element.enclosedElements).mapNotNull<ExecutableElement, AstMethod> { method ->
            if (kmClass != null) {
                for (property in kmClass.properties) {
                    if (method.matches(property)) {
                        return@mapNotNull ModelAstProperty(
                            this,
                            this,
                            method,
                            property
                        )
                    }
                }
                for (function in kmClass.functions) {
                    if (method.matches(function)) {
                        return@mapNotNull ModelAstFunction(
                            this,
                            this,
                            method,
                            function
                        )
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
) : AstConstructor(parent),
    ModelAstElement, ModelAstProvider by provider {

    override val parameters: List<AstParam> by lazy {
        val params = element.parameters
        val kmParams: List<KmValueParameter> = kmConstructor?.valueParameters ?: emptyList()
        params.mapIndexed { index, param ->
            val kmParam = kmParams.getOrNull(index)
            ModelAstParam(
                this,
                param,
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
    provider: ModelAstProvider,
    override val parent: AstClass,
    override val element: ExecutableElement,
    private val kmFunction: KmFunction
) : AstFunction(),
    ModelAstMethod, ModelAstProvider by provider {

    override val name: String get() = kmFunction.name

    override val modifiers: Set<AstModifier> by lazy {
        collectModifiers(
            kmFunction.flags
        )
    }

    override val returnType: AstType
        get() = ModelAstType(
            this,
            element.returnType,
            kmFunction.returnType
        )

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        require(enclosingClass is ModelAstClass)
        val declaredType = enclosingClass.element.asType() as DeclaredType
        val methodType = types.asMemberOf(declaredType, element) as ExecutableType
        return ModelAstType(
            this,
            methodType.returnType,
            kmFunction.returnType
        )
    }

    override val receiverParameterType: AstType?
        get() = kmFunction.receiverParameterType?.let {
            ModelAstType(this, element.parameters[0].asType(), it)
        }

    override val parameters: List<AstParam> by lazy {
        val params = if (kmFunction.receiverParameterType != null) {
            // drop the extension function receiver if present
            element.parameters.drop(1)
        } else {
            element.parameters
        }
        val kmParams: List<KmValueParameter> = kmFunction.valueParameters
        params.mapIndexed { index, param ->
            val kmParam = kmParams.getOrNull(index)
            ModelAstParam(
                this,
                param,
                kmParam
            )
        }
    }

    override fun overrides(other: AstMethod): Boolean {
        require(other is ModelAstMethod)
        return elements.overrides(element, other.element, parent.element)
    }

    override fun asMemberName(): MemberName {
        return MemberName(elements.getPackageOf(element).qualifiedName.toString(), name)
    }

    override fun equals(other: Any?): Boolean {
        return other is ModelAstFunction && element == other.element
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }
}

private class ModelAstProperty(
    provider: ModelAstProvider,
    override val parent: AstClass,
    override val element: ExecutableElement,
    private val kmProperty: KmProperty
) : AstProperty(),
    ModelAstMethod, ModelAstProvider by provider {

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
        get() = ModelAstType(
            this,
            element.returnType,
            kmProperty.returnType
        )

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        require(enclosingClass is ModelAstClass)
        val declaredType = enclosingClass.element.asType() as DeclaredType
        val methodType = types.asMemberOf(declaredType, element) as ExecutableType
        return ModelAstType(
            this,
            methodType.returnType,
            kmProperty.returnType
        )
    }

    override val receiverParameterType: AstType?
        get() = kmProperty.receiverParameterType?.let {
            ModelAstType(this, element.parameters[0].asType(), it)
        }

    override fun overrides(other: AstMethod): Boolean {
        require(other is ModelAstMethod)
        return elements.overrides(element, other.element, parent.element)
    }

    override fun asMemberName(): MemberName {
        return MemberName(elements.getPackageOf(element).qualifiedName.toString(), name)
    }

    override fun equals(other: Any?): Boolean {
        return other is ModelAstProperty && element == other.elements
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }
}

private class ModelAstType(
    provider: ModelAstProvider,
    val type: TypeMirror,
    val kmType: KmType?
) : AstType(),
    ModelAstElement, ModelAstProvider by provider {

    override val element: Element get() = types.asElement(type)

    override val packageName: String
        get() {
            if (kmType != null) {
                return kmType.packageName
            }
            return elements.getPackageOf(element).qualifiedName.toString()
        }

    override val simpleName: String
        get() {
            if (kmType != null) {
                return kmType.simpleName
            }
            return element.simpleName.toString()
        }

    override val annotations: List<AstAnnotation> by lazy {
        if (kmType != null) {
            kmType.annotations.map { annotation ->
                val mirror = provider.elements.getTypeElement(annotation.className.replace('/', '.'))
                ModelAstAnnotation(
                    this,
                    mirror.asType() as DeclaredType,
                    annotation
                )
            }
        } else {
            emptyList()
        }
    }

    override val arguments: List<AstType> by lazy {
        val kmArgs: List<KmType?> = kmType?.arguments?.map { it.type } ?: emptyList()
        val args: List<TypeMirror> = (type as DeclaredType).typeArguments
        if (args.size == kmArgs.size) {
            args.zip(kmArgs) { a1, a2 -> ModelAstType(this, a1, a2) }
        } else {
            args.map { ModelAstType(this, it, null) }
        }
    }

    override fun isUnit(): Boolean = type is NoType

    override fun isFunction(): Boolean {
        return element.toString().matches(Regex("kotlin\\.jvm\\.functions\\.Function[0-9]+.*"))
    }

    override fun isTypeAlis(): Boolean {
        return kmType?.abbreviatedType != null
    }

    override fun resolvedType(): AstType {
        val abbreviatedType = kmType?.abbreviatedType
        return if (abbreviatedType != null) {
            ModelAstType(this, type, abbreviatedType)
        } else {
            this
        }
    }

    override fun asElement(): AstBasicElement =
        ModelBasicElement(this, element)

    override fun toAstClass(): AstClass {
        return when (type) {
            is PrimitiveType -> PrimitiveModelAstClass(this)
            is ArrayType -> PrimitiveModelAstClass(this)
            is DeclaredType, is TypeVariable -> (types.asElement(type) as TypeElement).toAstClass()
            else -> throw IllegalStateException("unknown type: $type")
        }
    }

    override fun asTypeName(): TypeName {
        return type.asTypeName(kmType)
    }

    override fun isAssignableFrom(other: AstType): Boolean {
        require(other is ModelAstType)
        return types.isAssignable(other.type, type)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AstType) return false
        return asTypeName() == other.asTypeName()
    }

    override fun hashCode(): Int {
        return asTypeName().hashCode()
    }
}

private class ModelAstAnnotation(
    provider: ModelAstProvider,
    val annotationType: DeclaredType,
    private val kmAnnotation: KmAnnotation
) : AstAnnotation(),
    ModelAstElement, ModelAstProvider by provider {
    override val element: Element get() = types.asElement(annotationType)

    override fun equals(other: Any?): Boolean {
        if (other !is ModelAstAnnotation) return false
        return kmAnnotation == other.kmAnnotation
    }

    override fun hashCode(): Int {
        return kmAnnotation.hashCode()
    }

    override fun toString(): String {
        return "@$annotationType(${
            kmAnnotation.arguments.toList()
                .joinToString(separator = ", ") { (name, value) -> "$name=${value.value}" }
        })"
    }
}

private class ModelAstParam(
    provider: ModelAstProvider,
    override val element: VariableElement,
    val kmValueParameter: KmValueParameter?
) : AstParam(),
    ModelAstElement, ModelAstProvider by provider {

    override val name: String
        get() {
            return kmValueParameter?.name ?: element.simpleName.toString()
        }

    override val type: AstType by lazy {
        ModelAstType(this, element.asType(), kmValueParameter?.type)
    }

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

val AstClass.element: TypeElement
    get() {
        require(this is ModelAstClass)
        return element
    }
