package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import kotlin.reflect.KClass

interface AstProvider {
    val messenger: Messenger

    fun findFunctions(packageName: String, functionName: String): Sequence<AstFunction>

    fun declaredTypeOf(klass: KClass<*>, vararg astTypes: AstType): AstType

    fun warn(message: String, element: AstElement? = null) = messenger.warn(message, element)

    fun error(message: String, element: AstElement? = null) =
        messenger.error(message, element)

    fun AstElement.toTrace(): String
}

interface Messenger {
    fun warn(message: String, element: AstElement? = null)

    fun error(message: String, element: AstElement? = null)
}

sealed class AstElement : AstAnnotated

interface AstAnnotated {
    fun hasAnnotation(packageName: String, simpleName: String): Boolean

    fun annotationAnnotatedWith(packageName: String, simpleName: String): AstAnnotation?
}

abstract class AstBasicElement : AstElement() {
    abstract val simpleName: String
}

abstract class AstClass : AstElement(), AstHasModifiers {

    abstract val packageName: String

    abstract val name: String

    abstract val companion: AstClass?

    abstract val isObject: Boolean

    abstract val isInterface: Boolean

    abstract val superTypes: List<AstClass>

    abstract val primaryConstructor: AstConstructor?

    abstract val constructors: List<AstConstructor>

    abstract val methods: List<AstMethod>

    abstract val type: AstType

    fun visitInheritanceChain(f: (AstClass) -> Unit) {
        f(this)
        superTypes.forEach { it.visitInheritanceChain(f) }
    }

    abstract fun asClassName(): ClassName

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    override fun toString(): String {
        return if (packageName.isEmpty() || packageName == "kotlin") name else type.toString()
    }
}

sealed class AstMethod : AstElement(), AstHasModifiers {
    abstract val name: String

    abstract val receiverParameterType: AstType?

    abstract fun overrides(other: AstMethod): Boolean

    abstract val returnType: AstType

    abstract fun returnTypeFor(enclosingClass: AstClass): AstType

    abstract fun asMemberName(): MemberName
}

abstract class AstConstructor(private val parent: AstClass) : AstElement() {
    val type: AstType get() = parent.type

    abstract val parameters: List<AstParam>

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    override fun toString(): String {
        return "$parent(${parameters.joinToString(", ")})"
    }
}

abstract class AstFunction : AstMethod() {
    abstract val parameters: List<AstParam>

    abstract val isSuspend: Boolean

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    override fun toString(): String {
        return "$name(${parameters.joinToString(", ")}): $returnType"
    }
}

abstract class AstProperty : AstMethod() {

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    override fun toString(): String {
        return "$name: $returnType"
    }
}

private val DEFAULT_IMPORTS = arrayOf(
    "kotlin",
    "kotlin.annotation",
    "kotlin.collections",
    "kotlin.comparisons",
    "kotlin.io",
    "kotlin.ranges",
    "kotlin.sequences",
    "kotlin.text"
)

abstract class AstType : AstElement() {

    abstract val packageName: String

    abstract val simpleName: String

    abstract val annotations: List<AstAnnotation>

    abstract val arguments: List<AstType>

    abstract fun isUnit(): Boolean

    abstract fun isPlatform(): Boolean

    abstract fun isFunction(): Boolean

    abstract fun isTypeAlias(): Boolean

    abstract fun resolvedType(): AstType

    @Suppress("NOTHING_TO_INLINE")
    inline fun isNotUnit() = !isUnit()

    abstract fun asElement(): AstBasicElement

    abstract fun toAstClass(): AstClass

    abstract fun asTypeName(): TypeName

    abstract fun isAssignableFrom(other: AstType): Boolean

    final override fun toString(): String {
        val packageName = packageName
        return if (packageName in DEFAULT_IMPORTS) {
            asTypeName().toString().removePrefix("$packageName.")
        } else {
            asTypeName().toString()
        }
    }
}

abstract class AstAnnotation : AstElement() {
    abstract val type: AstType
}

abstract class AstParam : AstElement() {

    abstract val name: String

    abstract val type: AstType

    abstract val isVal: Boolean

    abstract val isPrivate: Boolean

    abstract val hasDefault: Boolean

    abstract fun asParameterSpec(): ParameterSpec

    override fun toString(): String {
        return "$name: $type"
    }
}

interface AstHasModifiers {

    val visibility: AstVisibility

    val isAbstract: Boolean
}

interface OutputProvider<Output> {

    fun astTypeSpec(typeSpecBuilder: TypeSpec.Builder, originatingElement: AstClass): AstTypeSpec

    fun astFileSpec(fileSpecBuilder: FileSpec.Builder, astTypeSpec: AstTypeSpec): AstFileSpec<Output>
}

interface AstTypeSpec {
    val typeSpec: TypeSpec
}

interface AstFileSpec<Output> {
    fun writeTo(output: Output)
}