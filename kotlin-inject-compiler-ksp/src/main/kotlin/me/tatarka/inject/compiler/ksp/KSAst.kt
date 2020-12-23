package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.tatarka.inject.compiler.*
import org.jetbrains.kotlin.analyzer.AnalysisResult
import kotlin.reflect.KClass

interface KSAstProvider : AstProvider, OutputProvider<CodeGenerator> {

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
        return KSAstType(
            this,
            declaration.asType(
                astTypes.map {
                    val type = (it as KSAstType).type
                    resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(type), Variance.INVARIANT)
                }
            )
        )
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

    override fun astTypeSpec(typeSpecBuilder: TypeSpec.Builder, originatingElement: AstClass): AstTypeSpec {
        require(originatingElement is KSAstClass)
        return KSAstTypeSpec(typeSpecBuilder, originatingElement)
    }

    override fun astFileSpec(fileSpecBuilder: FileSpec.Builder, astTypeSpec: AstTypeSpec): AstFileSpec<CodeGenerator> {
        require(astTypeSpec is KSAstTypeSpec)
        return KSAstFileSpec(fileSpecBuilder, astTypeSpec)
    }
}

object KSAstMessenger : Messenger {
    private val errorMessages = mutableListOf<Message>()
    internal lateinit var logger: KSPLogger

    override fun warn(message: String, element: AstElement?) {
        logger.warn(message, (element as? KSAstAnnotated)?.declaration)
    }

    override fun error(message: String, element: AstElement?) {
        logger.error(message, (element as? KSAstAnnotated)?.declaration)
        errorMessages.add(Message(message, element))
    }

    fun finalize() {
        // ksp won't error out if no exception is thrown
        if (errorMessages.isNotEmpty()) {
            throw AnalysisResult.CompilationErrorException()
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

    override fun annotationAnnotatedWith(className: String): AstAnnotation? {
        return declaration.annotationAnnotatedWith(className)?.let {
            KSAstAnnotation(this, it)
        }
    }
}

private class KSAstBasicElement(provider: KSAstProvider, override val declaration: KSDeclaration) :
    AstBasicElement(),
    KSAstAnnotated,
    KSAstProvider by provider {
    override val simpleName: String
        get() = declaration.simpleName.asString()
}

private class KSAstClass(provider: KSAstProvider, override val declaration: KSClassDeclaration) :
    AstClass(),
    KSAstAnnotated,
    KSAstProvider by provider {

    override val packageName: String
        get() = declaration.qualifiedName!!.getQualifier()

    override val name: String
        get() = declaration.simpleName.asString()

    override val visibility: AstVisibility
        get() = declaration.getVisibility().astVisibility()

    override val isAbstract: Boolean
        get() = declaration.isAbstract()

    override val isInterface: Boolean
        get() = declaration.classKind == ClassKind.INTERFACE

    override val companion: AstClass?
        get() = declaration.declarations
            .find { it is KSClassDeclaration && it.isCompanionObject }
            ?.let { KSAstClass(this, it as KSClassDeclaration) }

    override val isObject: Boolean
        get() = declaration.classKind == ClassKind.OBJECT

    override val superTypes: List<AstClass>
        get() {
            return declaration
                .superTypes
                .mapNotNull { type -> (type.resolve().declaration as? KSClassDeclaration)?.toAstClass() }
        }

    override val primaryConstructor: AstConstructor?
        get() {
            return declaration.primaryConstructor?.let { KSAstConstructor(this, this, it) }
        }

    override val constructors: List<AstConstructor>
        get() = declaration.getConstructors().map { KSAstConstructor(this, this, it) }

    override val methods: List<AstMethod>
        get() {
            return mutableListOf<AstMethod>().apply {
                addAll(
                    declaration.getDeclaredProperties().map {
                        KSAstProperty(this@KSAstClass, it)
                    }
                )
                addAll(
                    declaration.getDeclaredFunctions().map {
                        KSAstFunction(this@KSAstClass, it)
                    }
                )
            }
        }

    override val type: AstType
        get() {
            return KSAstType(this, declaration.asStarProjectedType())
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
    private val parent: KSAstClass,
    override val declaration: KSFunctionDeclaration
) : AstConstructor(parent), KSAstAnnotated, KSAstProvider by provider {

    override val parameters: List<AstParam>
        get() {
            return declaration.parameters.map { param -> KSAstParam(this, parent.declaration, declaration, param) }
        }

    override fun equals(other: Any?): Boolean {
        return other is KSAstConstructor && declaration == other.declaration
    }

    override fun hashCode(): Int {
        return declaration.hashCode()
    }
}

private class KSAstFunction(provider: KSAstProvider, override val declaration: KSFunctionDeclaration) :
    AstFunction(),
    KSAstAnnotated,
    KSAstProvider by provider {

    override val parameters: List<AstParam>
        get() = declaration.parameters.map { param ->
            KSAstParam(this, null, declaration, param)
        }

    override val name: String
        get() = declaration.simpleName.asString()

    override val visibility: AstVisibility
        get() = declaration.getVisibility().astVisibility()

    override val isAbstract: Boolean
        get() = declaration.isAbstract

    override val isSuspend: Boolean
        get() = declaration.modifiers.contains(Modifier.SUSPEND)

    override val receiverParameterType: AstType?
        get() = declaration.extensionReceiver?.let { KSAstType(this, it.resolve()) }

    override fun overrides(other: AstMethod): Boolean {
        if (other !is KSAstFunction) return false
        return resolver.overrides(declaration, other.declaration)
    }

    override val returnType: AstType
        get() = KSAstType(this, declaration.returnType!!.resolve())

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        require(enclosingClass is KSAstClass)
        return KSAstType(
            this,
            resolver.asMemberOf(declaration, enclosingClass.declaration.asStarProjectedType()).returnType!!
        )
    }

    override fun asMemberName(): MemberName {
        val packageName = declaration.qualifiedName?.getQualifier().orEmpty()
        return MemberName(packageName, declaration.simpleName.asString())
    }

    override fun equals(other: Any?): Boolean {
        return other is KSAstFunction && declaration == other.declaration
    }

    override fun hashCode(): Int {
        return declaration.hashCode()
    }
}

private class KSAstProperty(provider: KSAstProvider, override val declaration: KSPropertyDeclaration) :
    AstProperty(),
    KSAstAnnotated,
    KSAstProvider by provider {
    override val name: String
        get() = declaration.simpleName.asString()

    override val visibility: AstVisibility
        get() = declaration.getVisibility().astVisibility()

    override val isAbstract: Boolean
        get() = declaration.isAbstract()

    override val receiverParameterType: AstType?
        get() = declaration.extensionReceiver?.let { KSAstType(this, it.resolve()) }

    override fun overrides(other: AstMethod): Boolean {
        if (other !is KSAstProperty) return false
        return resolver.overrides(declaration, other.declaration)
    }

    override val returnType: AstType
        get() = KSAstType(this, declaration.type.resolve())

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        require(enclosingClass is KSAstClass)
        return KSAstType(
            this,
            resolver.asMemberOf(declaration, enclosingClass.declaration.asStarProjectedType())
        )
    }

    override fun asMemberName(): MemberName {
        val packageName = declaration.qualifiedName?.getQualifier().orEmpty()
        return MemberName(packageName, declaration.simpleName.toString())
    }

    override fun hasAnnotation(className: String): Boolean {
        return declaration.getter?.hasAnnotation(className) == true ||
                declaration.hasAnnotation(className, AnnotationUseSiteTarget.GET)
    }

    override fun annotationAnnotatedWith(className: String): AstAnnotation? {
        return (
                declaration.getter
                    ?.annotationAnnotatedWith(className)
                    ?: declaration.annotationAnnotatedWith(className, AnnotationUseSiteTarget.GET)
                )?.let { KSAstAnnotation(this, it) }
    }

    override fun equals(other: Any?): Boolean {
        return other is KSAstProperty && declaration == other.declaration
    }

    override fun hashCode(): Int {
        return declaration.hashCode()
    }
}

private class KSAstType(provider: KSAstProvider, val type: KSType) : AstType(), KSAstAnnotated,
    KSAstProvider by provider {

    override val declaration: KSDeclaration get() = type.declaration

    override val packageName: String
        get() = type.declaration.qualifiedName!!.getQualifier()

    override val simpleName: String
        get() = type.declaration.simpleName.asString()

    override val annotations: List<AstAnnotation>
        get() = TODO("Not yet implemented")

    override val arguments: List<AstType>
        get() = type.arguments.map {
            KSAstType(this, it.type!!.resolve())
        }

    override fun isUnit(): Boolean {
        return type == resolver.builtIns.unitType
    }

    override fun isFunction(): Boolean {
        val actualType = type.actualType()
        return actualType.isFunction() || actualType.isSuspendingFunction()
    }

    override fun isTypeAlis(): Boolean {
        return declaration is KSTypeAlias
    }

    override fun resolvedType(): AstType {
        val declaration = declaration
        return if (declaration is KSTypeAlias) {
            KSAstType(this, declaration.type.resolve())
        } else {
            this
        }
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
        // don't use type == other.type as the impl declares differing typealias as equal
        // and we don't want that behavior.
        return other is KSAstType && type.eqv(other.type)
    }

    override fun hashCode(): Int {
        return type.eqvHashCode()
    }

    private fun KSType.actualType(): KSType = when (val declaration = declaration) {
        is KSTypeAlias -> declaration.type.resolve().actualType()
        else -> this
    }
}

private class KSAstParam(
    provider: KSAstProvider,
    val parentClass: KSClassDeclaration?,
    val parent: KSDeclaration,
    override val declaration: KSValueParameter
) : AstParam(),
    KSAstAnnotated,
    KSAstProvider by provider {

    override val name: String
        get() = declaration.name!!.asString()

    override val type: AstType
        get() = KSAstType(this, declaration.type!!.resolve())

    override val isVal: Boolean
        get() = declaration.isVal

    override val isPrivate: Boolean
        get() = parentClass?.getDeclaredProperties()?.find { it.simpleName == declaration.name }?.isPrivate() ?: false

    override val hasDefault: Boolean
        get() = declaration.hasDefault

    override fun asParameterSpec(): ParameterSpec {
        return ParameterSpec.builder(name, type.asTypeName())
            .build()
    }

    override fun toString(): String = "$name: $type"
}

private class KSAstAnnotation(provider: KSAstProvider, val annotation: KSAnnotation) :
    AstAnnotation(),
    KSAstProvider by provider {

    override val type: AstType
        get() = KSAstType(this, annotation.annotationType.resolve())

    override fun hasAnnotation(className: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun annotationAnnotatedWith(className: String): AstAnnotation? {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        return other is KSAstAnnotation && annotation.eqv(other.annotation)
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun toString(): String {
        return "$annotation(${
            annotation.arguments.joinToString(", ") { arg -> "${arg.name?.asString()}=${arg.value}" }
        })"
    }
}

private fun Visibility.astVisibility(): AstVisibility =
    when (this) {
        Visibility.PUBLIC, Visibility.JAVA_PACKAGE -> AstVisibility.PUBLIC
        Visibility.PRIVATE -> AstVisibility.PRIVATE
        Visibility.PROTECTED -> AstVisibility.PROTECTED
        Visibility.INTERNAL -> AstVisibility.INTERNAL
        else -> throw UnsupportedOperationException("unsupported visibility: $this")
    }

private class KSAstTypeSpec(typeSpecBuilder: TypeSpec.Builder, val astClass: KSAstClass) : AstTypeSpec() {
    override val typeSpec: TypeSpec = typeSpecBuilder.build()
}

private class KSAstFileSpec(fileSpecBuilder: FileSpec.Builder, val typeSpec: KSAstTypeSpec) : AstFileSpec<CodeGenerator>() {
    private val fileSpec: FileSpec = fileSpecBuilder
        .addType(typeSpec.typeSpec)
        .build()

    override fun writeTo(output: CodeGenerator) {
        output.createNewFile(
            Dependencies(true, typeSpec.astClass.declaration.containingFile!!),
            fileSpec.packageName,
            fileSpec.name
        ).bufferedWriter().use { fileSpec.writeTo(it) }
    }
}