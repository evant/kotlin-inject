package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.findActualType
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.tatarka.inject.compiler.AstAnnotated
import me.tatarka.inject.compiler.AstAnnotation
import me.tatarka.inject.compiler.AstBasicElement
import me.tatarka.inject.compiler.AstClass
import me.tatarka.inject.compiler.AstConstructor
import me.tatarka.inject.compiler.AstElement
import me.tatarka.inject.compiler.AstFileSpec
import me.tatarka.inject.compiler.AstFunction
import me.tatarka.inject.compiler.AstMethod
import me.tatarka.inject.compiler.AstParam
import me.tatarka.inject.compiler.AstProperty
import me.tatarka.inject.compiler.AstProvider
import me.tatarka.inject.compiler.AstType
import me.tatarka.inject.compiler.AstTypeSpec
import me.tatarka.inject.compiler.AstVisibility
import me.tatarka.inject.compiler.Messenger
import me.tatarka.inject.compiler.OutputProvider

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

    override fun findFunctions(packageName: String, functionName: String): Sequence<AstFunction> {
        val name = resolver.getKSNameFromString("$packageName.$functionName")
        return resolver.getFunctionDeclarationsByName(name, includeTopLevel = true)
            .map { declaration ->
                KSAstFunction(this, declaration)
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
    internal lateinit var logger: KSPLogger

    override fun warn(message: String, element: AstElement?) {
        logger.warn(message, (element as? KSAstAnnotated)?.declaration)
    }

    override fun error(message: String, element: AstElement?) {
        logger.error(message, (element as? KSAstAnnotated)?.declaration)
    }
}

private interface KSAstAnnotated : AstAnnotated, KSAstProvider {
    val declaration: KSAnnotated

    override fun hasAnnotation(packageName: String, simpleName: String): Boolean {
        return declaration.hasAnnotation(packageName, simpleName)
    }

    override fun annotationAnnotatedWith(packageName: String, simpleName: String): AstAnnotation? {
        return declaration.annotationAnnotatedWith(packageName, simpleName)?.let {
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
        get() = declaration.simplePackageName()

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
        return declaration.asClassName()
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
        // resolver.asMemberOf is expensive and only matters if there are generics involved, so check that first.
        val type = declaration.returnType!!.resolve()
        if (type.isConcrete()) {
            return KSAstType(this, type)
        }
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
        // resolver.asMemberOf is expensive and only matters if there are generics involved, so check that first.
        val type = declaration.type.resolve()
        if (type.isConcrete()) {
            return KSAstType(this, type)
        }
        return KSAstType(
            this,
            resolver.asMemberOf(declaration, enclosingClass.declaration.asStarProjectedType())
        )
    }

    override fun asMemberName(): MemberName {
        val packageName = declaration.qualifiedName?.getQualifier().orEmpty()
        return MemberName(packageName, declaration.simpleName.toString())
    }

    override fun hasAnnotation(packageName: String, simpleName: String): Boolean {
        return declaration.getter?.hasAnnotation(packageName, simpleName) == true
    }

    override fun annotationAnnotatedWith(packageName: String, simpleName: String): AstAnnotation? {
        return declaration.getter?.annotationAnnotatedWith(packageName, simpleName)?.let {
            KSAstAnnotation(this, it)
        }
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
        get() = type.declaration.simplePackageName()

    override val simpleName: String
        get() = type.declaration.shortName

    override val annotations: List<AstAnnotation>
        get() = TODO("Not yet implemented")

    override val arguments: List<AstType>
        get() = type.arguments.map {
            KSAstType(this, it.type!!.resolve())
        }

    override fun isUnit(): Boolean {
        return type == resolver.builtIns.unitType
    }

    override fun isPlatform(): Boolean = type.nullability == Nullability.PLATFORM

    override fun isFunction(): Boolean {
        val actualType = type.actualType()
        return actualType.isFunction() || actualType.isSuspendingFunction()
    }

    override fun isTypeAlias(): Boolean {
        return declaration is KSTypeAlias
    }

    override fun resolvedType(): AstType {
        val declaration = declaration
        return if (declaration is KSTypeAlias) {
            KSAstType(this, declaration.type.resolve().replace(type.arguments))
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
        get() = KSAstType(this, declaration.type.resolve())

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

    override fun hasAnnotation(packageName: String, simpleName: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun annotationAnnotatedWith(packageName: String, simpleName: String): AstAnnotation? {
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

private class KSAstTypeSpec(typeSpecBuilder: TypeSpec.Builder, val astClass: KSAstClass) : AstTypeSpec {
    override val typeSpec: TypeSpec = typeSpecBuilder.build()
}

private class KSAstFileSpec(fileSpecBuilder: FileSpec.Builder, val typeSpec: KSAstTypeSpec) :
    AstFileSpec<CodeGenerator> {
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