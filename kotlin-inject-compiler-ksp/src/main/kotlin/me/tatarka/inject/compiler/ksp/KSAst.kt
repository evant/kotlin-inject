package me.tatarka.inject.compiler.ksp

import com.squareup.kotlinpoet.*
import me.tatarka.inject.compiler.*
import org.jetbrains.kotlin.ksp.findActualType
import org.jetbrains.kotlin.ksp.getDeclaredFunctions
import org.jetbrains.kotlin.ksp.getDeclaredProperties
import org.jetbrains.kotlin.ksp.isPrivate
import org.jetbrains.kotlin.ksp.processing.KSPLogger
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.symbol.*
import kotlin.reflect.KClass

interface KSAstProvider : AstProvider {

    val resolver: Resolver

    val logger: KSPLogger

    override val messenger: KSAstMessenger
        get() = KSAstMessenger.also {
            it.logger = logger
        }

    fun KSClassDeclaration.toAstClass(): AstClass {
        return KSAstClass(this@KSAstProvider, this)
    }

    override fun findFunctions(packageName: String, functionName: String): List<AstFunction> {
        return resolver.getAllFiles().filter { it.packageName.asString() == packageName }
            .flatMap { it.declarations }
            .mapNotNull { declaration ->
                if (declaration is KSFunctionDeclaration && declaration.simpleName.asString() == functionName) {
                    KSAstFunction(this, declaration)
                } else {
                    null
                }
            }
    }

    override fun declaredTypeOf(klass: KClass<*>, vararg astTypes: AstType): AstType {
        val declaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(klass.qualifiedName!!))!!
        return KSAstType(this, declaration.asType(astTypes.map {
            resolver.getTypeArgument((it as KSAstType).typeRef!!, Variance.INVARIANT)
        }))
    }

    override fun AstElement.toTrace(): String {
        require(this is KSAstAnnotated)
        val source = declaration.location as? FileLocation
        return if (source != null) {
            "${source.filePath}:${source.lineNumber}: ${toString()}"
        } else {
            toString()
        }
    }

    override fun TypeSpec.Builder.addOriginatingElement(astClass: AstClass): TypeSpec.Builder = this
}

object KSAstMessenger : Messenger {
    private val errorMessages = mutableListOf<Message>()
    internal lateinit var logger: KSPLogger

    override fun warn(message: String, element: AstElement) {
        logger.warn(message, (element as KSAstAnnotated).declaration)
    }

    override fun error(message: String, element: AstElement) {
        logger.error(message, (element as KSAstAnnotated).declaration)
        errorMessages.add(Message(message, element))
    }

    fun finalize() {
        // ksp won't error out if no exception is thrown
        if (errorMessages.isNotEmpty()) {
            throw IllegalStateException()
        }
    }

    private data class Message(val message: String, val element: AstElement?) {
        override fun toString(): String = StringBuilder().apply {
            val containing = when (element) {
                is KSAstFunction -> element.declaration.parentDeclaration
                is KSAstConstructor -> element.declaration.parentDeclaration
                is KSAstProperty -> element.declaration.parentDeclaration
                is KSAstParam -> element.parent
                else -> null
            }
            if (containing != null) {
                if (containing.qualifiedName != null) {
                    append(containing.qualifiedName?.asString())
                } else {
                    append(containing.parentDeclaration?.qualifiedName?.asString())
                }
                append(": ")
            }
            append(message)
            if (element != null) {
                append("\n\t")
                append(element)
            }
        }.toString()
    }
}

private interface KSAstAnnotated : AstAnnotated, KSAstProvider {
    val declaration: KSAnnotated

    override fun hasAnnotation(className: String): Boolean {
        return declaration.hasAnnotation(className)
    }

    override fun typeAnnotatedWith(className: String): AstClass? {
        return (declaration.typeAnnotatedWith(className)?.declaration as? KSClassDeclaration)?.toAstClass()
    }
}

private class KSAstBasicElement(provider: KSAstProvider, override val declaration: KSDeclaration) : AstBasicElement(),
    KSAstAnnotated, KSAstProvider by provider {
    override val simpleName: String
        get() = declaration.simpleName.asString()
}

private class KSAstClass(provider: KSAstProvider, override val declaration: KSClassDeclaration) : AstClass(),
    KSAstAnnotated, KSAstProvider by provider {

    override val packageName: String
        get() = declaration.qualifiedName!!.getQualifier()

    override val name: String
        get() = declaration.simpleName.asString()

    override val modifiers: Set<AstModifier> by lazy {
        collectModifiers(declaration)
    }

    override val companion: AstClass?
        get() = TODO("Not yet implemented")

    override val superTypes: List<AstClass> by lazy {
        declaration.superTypes.mapNotNull { type -> (type.resolve()?.declaration as? KSClassDeclaration)?.toAstClass() }
    }

    override val primaryConstructor: AstConstructor? by lazy {
        declaration.primaryConstructor?.let { KSAstConstructor(this, this, it) }
            ?: KSAstEmptyConstructor(this, this)
    }

    override val methods: List<AstMethod> by lazy {
        mutableListOf<AstMethod>().apply {
            addAll(declaration.getDeclaredProperties().map {
                KSAstProperty(this@KSAstClass, it)
            })
            addAll(declaration.getDeclaredFunctions().map {
                KSAstFunction(this@KSAstClass, it)
            })
        }
    }

    override val type: AstType by lazy {
        KSAstType(this, declaration.asStarProjectedType())
    }

    override fun asClassName(): ClassName {
        return ClassName(packageName, name)
    }

    override fun equals(other: Any?): Boolean {
        return other is KSAstClass && declaration == other.declaration
    }

    override fun hashCode(): Int {
        return declaration.hashCode()
    }
}

private class KSAstConstructor(
    provider: KSAstProvider,
    private val parent: AstClass,
    override val declaration: KSFunctionDeclaration
) : AstConstructor(parent), KSAstAnnotated, KSAstProvider by provider {

    override val parameters: List<AstParam> by lazy {
        declaration.parameters.map { param -> KSAstParam(this, declaration, param) }
    }

    override fun equals(other: Any?): Boolean {
        return other is KSAstConstructor && declaration == other.declaration
    }

    override fun hashCode(): Int {
        return declaration.hashCode()
    }
}

private class KSAstEmptyConstructor(
    provider: KSAstProvider,
    private val parent: AstClass
) : AstConstructor(parent), KSAstProvider by provider {

    override val parameters: List<AstParam> = emptyList()

    override fun hasAnnotation(className: String): Boolean = false

    override fun typeAnnotatedWith(className: String): AstClass? = null

    override fun equals(other: Any?): Boolean {
        return other is KSAstEmptyConstructor && parent == other.parent
    }

    override fun hashCode(): Int {
        return parent.hashCode()
    }
}

private class KSAstFunction(provider: KSAstProvider, override val declaration: KSFunctionDeclaration) : AstFunction(),
    KSAstAnnotated, KSAstProvider by provider {

    override val parameters: List<AstParam>
        get() = declaration.parameters.map { param ->
            KSAstParam(this, declaration, param)
        }

    override val name: String
        get() = declaration.simpleName.asString()

    override val modifiers: Set<AstModifier> by lazy {
        collectModifiers(declaration)
    }
    override val receiverParameterType: AstType?
        get() = declaration.extensionReceiver?.let { KSAstType(this, it) }

    override fun overrides(other: AstMethod): Boolean {
        require(other is KSAstFunction)
        return declaration.overrides(other.declaration)
    }

    override val returnType: AstType
        get() = KSAstType(this, declaration.returnType!!)

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        return KSAstType(this, declaration.returnType!!)
    }

    override fun asMemberName(): MemberName {
        val packageName = declaration.qualifiedName?.getQualifier().orEmpty()
        return MemberName(packageName, declaration.simpleName.toString())
    }

    override fun equals(other: Any?): Boolean {
        return other is KSAstFunction && declaration == other.declaration
    }

    override fun hashCode(): Int {
        return declaration.hashCode()
    }
}

private class KSAstProperty(provider: KSAstProvider, override val declaration: KSPropertyDeclaration) : AstProperty(),
    KSAstAnnotated, KSAstProvider by provider {
    override val name: String
        get() = declaration.simpleName.asString()

    override val modifiers: Set<AstModifier> by lazy {
        collectModifiers(declaration)
    }

    override val receiverParameterType: AstType?
        get() = declaration.extensionReceiver?.let { KSAstType(this, it) }

    override fun overrides(other: AstMethod): Boolean {
        require(other is KSAstProperty)
        return false //TODO?
    }

    override val returnType: AstType
        get() = KSAstType(this, declaration.type!!)

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        require(enclosingClass is KSAstClass)
        return KSAstType(this, declaration.type!!.memberOf(enclosingClass.declaration))
    }

    override fun asMemberName(): MemberName {
        val packageName = declaration.qualifiedName?.getQualifier().orEmpty()
        return MemberName(packageName, declaration.simpleName.toString())
    }

    override fun equals(other: Any?): Boolean {
        return other is KSAstProperty && declaration == other.declaration
    }

    override fun hashCode(): Int {
        return declaration.hashCode()
    }
}

private class KSAstType(provider: KSAstProvider) : AstType(), KSAstAnnotated, KSAstProvider by provider {

    lateinit var type: KSType
    var typeRef: KSTypeReference? = null
        private set

    constructor(provider: KSAstProvider, typeRef: KSTypeReference) : this(provider) {
        this.type = typeRef.resolve()!!
        this.typeRef = typeRef
    }

    constructor(provider: KSAstProvider, type: KSType) : this(provider) {
        this.type = type
        this.typeRef = null
    }

    override val declaration: KSDeclaration get() = type.declaration

    override val name: String by lazy {
        when (val declaration = type.declaration) {
            is KSClassDeclaration -> declaration.qualifiedName!!.asString()
            is KSTypeAlias -> declaration.findActualType().qualifiedName!!.asString()
            else -> throw IllegalArgumentException("unknown declaration: $declaration")
        }
    }

    override val annotations: List<AstAnnotation>
        get() = TODO("Not yet implemented")

    override val typeAliasName: String?
        get() = (declaration as? KSTypeAlias)?.qualifiedName?.asString()

    override val arguments: List<AstType>
        get() = type.arguments.map {
            KSAstType(this, it.type!!)
        }

    override fun isUnit(): Boolean {
        return type == resolver.builtIns.unitType
    }

    override fun asElement(): AstBasicElement {
        return KSAstBasicElement(this, declaration)
    }

    override fun toAstClass(): AstClass {
        return when (val declaration = declaration) {
            is KSClassDeclaration -> declaration.toAstClass()
            is KSTypeAlias -> declaration.findActualType().toAstClass()
            else -> throw IllegalArgumentException("unknown declaration: $declaration")
        }
    }

    override fun asTypeName(): TypeName {
        return type.asTypeName()
    }

    override fun isAssignableFrom(other: AstType): Boolean {
        require(other is KSAstType)
        return type.isAssignableFrom(other.type)
    }

    override fun equals(other: Any?): Boolean {
        return other is KSAstType && type == other.type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }
}

private class KSAstParam(
    provider: KSAstProvider,
    val parent: KSDeclaration,
    override val declaration: KSVariableParameter
) : AstParam(),
    KSAstAnnotated, KSAstProvider by provider {

    override val name: String
        get() = declaration.name!!.asString()

    override val type: AstType
        get() = KSAstType(this, declaration.type!!.resolve()!!)

    override fun asParameterSpec(): ParameterSpec {
        return ParameterSpec.builder(name, type.asTypeName())
            .build()
    }

    override fun toString(): String = "$name: $type"
}

private fun collectModifiers(declaration: KSDeclaration): Set<AstModifier> {
    return mutableSetOf<AstModifier>().apply {
        if (declaration.isPrivate()) {
            add(AstModifier.PRIVATE)
        }
        if (declaration.isAbstract()) {
            add(AstModifier.ABSTRACT)
        }
    }
}
