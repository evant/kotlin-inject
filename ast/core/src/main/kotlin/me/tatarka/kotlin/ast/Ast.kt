package me.tatarka.kotlin.ast

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
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

    fun error(message: String, element: AstElement? = null) = messenger.error(message, element)

    fun AstElement.toTrace(): String

    fun FunSpec.Builder.addOriginatingElement(element: AstFunction): FunSpec.Builder
    fun TypeSpec.Builder.addOriginatingElement(element: AstClass): TypeSpec.Builder
}

interface Messenger {
    fun warn(message: String, element: AstElement? = null)

    fun error(message: String, element: AstElement? = null)
}

sealed class AstElement

interface AstAnnotated {
    fun hasAnnotation(packageName: String, simpleName: String): Boolean

    fun annotation(packageName: String, simpleName: String): AstAnnotation?

    fun annotationsAnnotatedWith(packageName: String, simpleName: String): Sequence<AstAnnotation>
}

fun AstAnnotated.hasAnnotation(annotation: ClassName) = hasAnnotation(annotation.packageName, annotation.simpleName)

fun AstAnnotated.annotationAnnotatedWith(packageName: String, simpleName: String): AstAnnotation? =
    annotationsAnnotatedWith(packageName, simpleName).firstOrNull()

abstract class AstBasicElement : AstElement() {
    abstract val simpleName: String
}

abstract class AstClass : AstElement(), AstAnnotated, AstHasModifiers {
    abstract val isJavaClass: Boolean

    abstract val packageName: String

    abstract val containingFile: AstAnnotated?

    abstract val name: String

    abstract val companion: AstClass?

    abstract val isObject: Boolean

    abstract val isInterface: Boolean

    abstract val superTypes: Sequence<AstClass>

    abstract val primaryConstructor: AstConstructor?

    abstract val constructors: Sequence<AstConstructor>

    abstract val methods: List<AstMember>

    /**
     * Returns methods of this class and it's superclasses.
     */
    abstract val allMethods: List<AstMember>

    abstract val outerClass: AstClass?

    abstract val type: AstType

    fun inheritanceChain(): Sequence<AstClass> = sequence {
        yield(this@AstClass)
        for (type in superTypes) {
            yieldAll(type.inheritanceChain())
        }
    }

    fun isInstanceOf(packageName: String, name: String): Boolean {
        return inheritanceChain().any {
            it.packageName == packageName && it.name == name
        }
    }

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    override fun toString(): String {
        return type.toString()
    }

    abstract fun toClassName(): ClassName
}

sealed class AstMember : AstElement(), AstAnnotated, AstHasModifiers {
    abstract val name: String

    abstract val receiverParameterType: AstType?

    abstract fun overrides(other: AstMember): Boolean

    abstract fun signatureEquals(other: AstMember): Boolean

    abstract val returnType: AstType

    abstract fun returnTypeFor(enclosingClass: AstClass): AstType

    abstract fun toMemberName(): MemberName
}

abstract class AstConstructor(private val parent: AstClass) : AstElement(), AstAnnotated {
    val type: AstType get() = parent.type

    val supportsNamedArguments get() = !parent.isJavaClass

    abstract val parameters: List<AstParam>

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    override fun toString(): String {
        return "$parent(${parameters.joinToString(", ")})"
    }
}

abstract class AstFunction : AstMember() {
    abstract val annotations: Sequence<AstAnnotation>

    abstract val parameters: List<AstParam>

    abstract val isSuspend: Boolean

    abstract val packageName: String

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    override fun toString(): String = buildString {
        receiverParameterType?.let {
            append(it)
            append(".")
        }
        append(name)
        append("(")
        append(parameters.joinToString())
        append(")")
        if (returnType.isNotUnit()) {
            append(": ")
            append(returnType)
        }
    }
}

abstract class AstProperty : AstMember() {

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    override fun toString(): String = buildString {
        receiverParameterType?.let {
            append(it)
            append(".")
        }
        append(name)
        if (returnType.isNotUnit()) {
            append(": ")
            append(returnType)
        }
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

abstract class AstType : AstElement(), AstAnnotated {

    abstract val packageName: String

    abstract val simpleName: String

    /**
     * Returns a list of type arguments. This method may throw an [IllegalStateException] if the type
     * cannot be resolved.
     */
    abstract val arguments: List<AstType>

    abstract val isError: Boolean

    abstract fun isUnit(): Boolean

    abstract fun isPlatform(): Boolean

    abstract fun isFunction(): Boolean

    abstract fun isTypeAlias(): Boolean

    abstract fun resolvedType(): AstType

    @Suppress("NOTHING_TO_INLINE")
    inline fun isNotUnit() = !isUnit()

    abstract fun asElement(): AstBasicElement

    abstract fun toAstClass(): AstClass

    abstract fun isAssignableFrom(other: AstType): Boolean

    protected fun String.shortenPackage(): String {
        return if (packageName in DEFAULT_IMPORTS) {
            removePrefix("$packageName.")
        } else {
            this
        }
    }

    abstract fun toTypeName(): TypeName

    abstract fun makeNonNullable(): AstType
}

abstract class AstAnnotation : AstElement() {
    abstract val type: AstType

    abstract fun toAnnotationSpec(): AnnotationSpec

    abstract fun argument(name: String): Any?
}

abstract class AstParam : AstElement(), AstAnnotated {

    abstract val name: String

    abstract val type: AstType

    abstract val isVal: Boolean

    abstract val isPrivate: Boolean

    abstract val hasDefault: Boolean

    override fun toString(): String {
        return "$name: $type"
    }

    fun toParameterSpec(): ParameterSpec {
        return ParameterSpec(name, type.toTypeName())
    }
}

interface AstHasModifiers {
    val visibility: AstVisibility

    val isAbstract: Boolean
    val isActual: Boolean
    val isExpect: Boolean
}

enum class AstVisibility {
    PUBLIC, PRIVATE, PROTECTED, INTERNAL;

    fun toKModifier(): KModifier = when (this) {
        PUBLIC -> KModifier.PUBLIC
        PRIVATE -> KModifier.PRIVATE
        PROTECTED -> KModifier.PROTECTED
        INTERNAL -> KModifier.INTERNAL
    }
}
