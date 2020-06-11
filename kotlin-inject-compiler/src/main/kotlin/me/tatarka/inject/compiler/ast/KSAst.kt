package me.tatarka.inject.compiler.ast

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.tatarka.inject.compiler.ksp.asTypeName
import me.tatarka.inject.compiler.ksp.getAnnotation
import me.tatarka.inject.compiler.ksp.typeAnnotatedWith
import org.jetbrains.kotlin.ksp.getDeclaredFunctions
import org.jetbrains.kotlin.ksp.getDeclaredProperties
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.symbol.*
import kotlin.reflect.KClass

interface KSAstProvider : AstProvider {

    val resolver: Resolver

    fun KSClassDeclaration.toAstClass(): AstClass {
        return KSAstClass(this@KSAstProvider, this)
    }

    override fun KClass<*>.toAstClass(): AstClass {
        TODO("Not yet implemented")
    }

    override fun declaredTypeOf(klass: KClass<*>, vararg astTypes: AstType): AstType {
        val declaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(klass.qualifiedName!!))!!
        return KSAstType(this, declaration.asType(astTypes.map {
            resolver.getTypeArgument((it as KSAstType).typeRef!!, Variance.INVARIANT)
        }))
    }

    override fun TypeSpec.Builder.addOriginatingElement(astClass: AstClass): TypeSpec.Builder = this

    override fun warn(message: String, element: AstElement?) {
        //TODO https://github.com/android/kotlin/issues/1
        println("$message: $element")
    }

    override fun error(message: String, element: AstElement?) {
        //TODO https://github.com/android/kotlin/issues/1
        System.err.println("$message: $element")
    }
}

private interface KSAstAnnotated : AstAnnotated, KSAstProvider {
    val declaration: KSAnnotated

    override fun <T : Annotation> hasAnnotation(kclass: KClass<T>): Boolean {
        return declaration.getAnnotation(kclass) != null
    }

    override fun <T : Annotation> typeAnnotatedWith(kclass: KClass<T>): AstClass? {
        return (declaration.typeAnnotatedWith(kclass)?.declaration as? KSClassDeclaration)?.toAstClass()
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
        collectModifiers(declaration.modifiers)
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
}

private class KSAstConstructor(
    provider: KSAstProvider,
    parent: AstClass,
    override val declaration: KSFunctionDeclaration
) : AstConstructor(parent), KSAstAnnotated, KSAstProvider by provider {

    override val parameters: List<AstParam> by lazy {
        declaration.parameters.map { param -> KSAstParam(this, param) }
    }
}

private class KSAstEmptyConstructor(
    provider: KSAstProvider,
    parent: AstClass
) : AstConstructor(parent), KSAstProvider by provider {

    override val parameters: List<AstParam> = emptyList()

    override fun <T : Annotation> hasAnnotation(kclass: KClass<T>): Boolean = false

    override fun <T : Annotation> typeAnnotatedWith(kclass: KClass<T>): AstClass? = null
}

private class KSAstFunction(provider: KSAstProvider, override val declaration: KSFunctionDeclaration) : AstFunction(),
    KSAstAnnotated, KSAstProvider by provider {

    override val parameters: List<AstParam>
        get() = declaration.parameters.map { param ->
            KSAstParam(this, param)
        }

    override val name: String
        get() = declaration.simpleName.asString()

    override val modifiers: Set<AstModifier> by lazy {
        collectModifiers(declaration.modifiers)
    }
    override val receiverParameterType: AstType?
        get() = declaration.extensionReceiver?.let { KSAstType(this, it) }

    override val returnType: AstType
        get() = KSAstType(this, declaration.returnType!!)

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        return KSAstType(this, declaration.returnType!!)
    }
}

private class KSAstProperty(provider: KSAstProvider, override val declaration: KSPropertyDeclaration) : AstProperty(),
    KSAstAnnotated, KSAstProvider by provider {
    override val name: String
        get() = declaration.simpleName.asString()

    override val modifiers: Set<AstModifier> by lazy {
        collectModifiers(declaration.modifiers)
    }

    override val receiverParameterType: AstType?
        get() = declaration.extensionReceiver?.let { KSAstType(this, it) }

    override val returnType: AstType
        get() = KSAstType(this, declaration.type!!)

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        return KSAstType(this, declaration.type!!)
    }
}

private class KSAstType(provider: KSAstProvider) : AstType(), KSAstAnnotated, KSAstProvider by provider {

    private lateinit var type: KSType
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

    override val name: String
        get() = declaration.asTypeName().toString()

    override val annotations: List<AstAnnotation>
        get() = TODO("Not yet implemented")

    override val abbreviatedTypeName: String?
        get() = (declaration as? KSTypeAlias)?.name?.asString()

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
        return (declaration as KSClassDeclaration).toAstClass()
    }

    override fun asTypeName(): TypeName {
        return abbreviatedTypeName?.let { ClassName.bestGuess(it) }
            ?: type.declaration.asTypeName()
    }
}

private class KSAstParam(provider: KSAstProvider, override val declaration: KSVariableParameter) : AstParam(),
    KSAstAnnotated, KSAstProvider by provider {

    override val name: String
        get() = declaration.name!!.asString()

    override val type: AstType
        get() = KSAstType(this, declaration.type!!.resolve()!!)

    override fun asParameterSpec(): ParameterSpec {
        return ParameterSpec.builder(name, type.asTypeName())
            .build()
    }
}

private fun collectModifiers(modifiers: Set<Modifier>): Set<AstModifier> {
    return modifiers.mapNotNull {
        when (it) {
            Modifier.PRIVATE -> AstModifier.PRIVATE
            Modifier.ABSTRACT -> AstModifier.ABSTRACT
            else -> null
        }
    }.toSet()
}