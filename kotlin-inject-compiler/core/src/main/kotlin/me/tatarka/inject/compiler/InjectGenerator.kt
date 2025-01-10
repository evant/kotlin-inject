package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import me.tatarka.kotlin.ast.AstAnnotated
import me.tatarka.kotlin.ast.AstAnnotation
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstConstructor
import me.tatarka.kotlin.ast.AstElement
import me.tatarka.kotlin.ast.AstFunction
import me.tatarka.kotlin.ast.AstMember
import me.tatarka.kotlin.ast.AstProperty
import me.tatarka.kotlin.ast.AstProvider
import me.tatarka.kotlin.ast.AstType
import me.tatarka.kotlin.ast.AstVisibility
import me.tatarka.kotlin.ast.Messenger
import me.tatarka.kotlin.ast.annotationAnnotatedWith
import me.tatarka.kotlin.ast.hasAnnotation

private const val ANNOTATION_PACKAGE_NAME = "me.tatarka.inject.annotations"
val COMPONENT = ClassName(ANNOTATION_PACKAGE_NAME, "Component")
val PROVIDES = ClassName(ANNOTATION_PACKAGE_NAME, "Provides")
val SCOPE = ClassName(ANNOTATION_PACKAGE_NAME, "Scope")
val INJECT = ClassName(ANNOTATION_PACKAGE_NAME, "Inject")
val INTO_MAP = ClassName(ANNOTATION_PACKAGE_NAME, "IntoMap")
val INTO_SET = ClassName(ANNOTATION_PACKAGE_NAME, "IntoSet")
val ASSISTED = ClassName(ANNOTATION_PACKAGE_NAME, "Assisted")
val ASSISTED_FACTORY = ClassName(ANNOTATION_PACKAGE_NAME, "AssistedFactory")
const val ASSISTED_FACTORY_FUNCTION_ARG = "injectFunction"
val QUALIFIER = ClassName(ANNOTATION_PACKAGE_NAME, "Qualifier")
val KMP_COMPONENT_CREATE = ClassName(ANNOTATION_PACKAGE_NAME, "KmpComponentCreate")

val JAVAX_SCOPE = ClassName("javax.inject", "Scope")
val JAVAX_INJECT = ClassName("javax.inject", "Inject")
val JAVAX_QUALIFIER = ClassName("javax.inject", "Qualifier")

val SCOPED_COMPONENT = ClassName("me.tatarka.inject.internal", "ScopedComponent")
val LAZY_MAP = ClassName("me.tatarka.inject.internal", "LazyMap")

val OPT_IN = ClassName("kotlin", "OptIn")

class InjectGenerator(
    private val provider: AstProvider,
    private val options: Options,
) {

    private val createGenerator = CreateGenerator(provider, options)
    private val typeCollector = TypeCollector(provider, options)

    var scope: AstAnnotation? = null
        private set

    fun generate(astClass: AstClass): FileSpec {
        if (!astClass.isAbstract) {
            throw FailedToGenerateException("@Component class: $astClass must be abstract", astClass)
        } else if (astClass.visibility == AstVisibility.PRIVATE) {
            throw FailedToGenerateException("@Component class: $astClass must not be private", astClass)
        }

        val classOptIn = astClass.optInAnnotation()
        val constructor = astClass.primaryConstructor

        val injectName = astClass.toInjectName()
        val injectComponent = generateInjectComponent(astClass, injectName, constructor, classOptIn)
        val createFunction = createGenerator.create(astClass, constructor, injectComponent, classOptIn)

        return FileSpec.builder(astClass.packageName, injectName).apply {
            addAnnotation(createSuppressAnnotation("REDUNDANT_VISIBILITY_MODIFIER"))
            astClass.containingFile?.optInAnnotation()?.let { addAnnotation(it) }
            createFunction.forEach { addFunction(it) }
            addType(injectComponent)
        }.build()
    }

    @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth")
    private fun generateInjectComponent(
        astClass: AstClass,
        injectName: String,
        constructor: AstConstructor?,
        optIn: AnnotationSpec?,
    ): TypeSpec {
        val context = collectTypes(astClass, injectName)
        val resolver = TypeResultResolver(provider, options)
        val scope = context.types.scopeClass
        this.scope = scope?.scope(options)

        return with(provider) {
            TypeSpec.classBuilder(context.className)
                .addOriginatingElement(astClass)
                .apply {
                    if (optIn != null) {
                        addAnnotation(optIn)
                    }
                    if (astClass.isInterface) {
                        addSuperinterface(astClass.toClassName())
                    } else {
                        superclass(astClass.toClassName())
                    }
                    if (scope != null) {
                        addSuperinterface(SCOPED_COMPONENT)
                    }
                    addModifiers(astClass.visibility.toKModifier())
                    if (constructor != null && constructor.parameters.isNotEmpty()) {
                        val funSpec = FunSpec.constructorBuilder()
                        val params = constructor.parameters
                        for (param in params) {
                            if (param.isComponent()) {
                                if (!param.isVal) {
                                    error("@Component parameter: ${param.name} must be val", param)
                                } else if (param.isPrivate) {
                                    error("@Component parameter: ${param.name} must not be private", param)
                                }
                            }
                        }
                        val paramSpecs = params.map { it.toParameterSpec() }
                        val nonDefaultParamSpecs =
                            constructor.parameters.filter { !it.hasDefault }.map { it.toParameterSpec() }
                        if (paramSpecs.size == nonDefaultParamSpecs.size) {
                            for (p in paramSpecs) {
                                funSpec.addParameter(p)
                                addSuperclassConstructorParameter("%N", p)
                            }
                            primaryConstructor(funSpec.build())
                        } else {
                            addFunction(
                                FunSpec.constructorBuilder()
                                    .addParameters(paramSpecs)
                                    .callSuperConstructor(paramSpecs.map { CodeBlock.of("%N", it) })
                                    .build()
                            )
                            addFunction(
                                FunSpec.constructorBuilder()
                                    .addParameters(nonDefaultParamSpecs)
                                    .callSuperConstructor(nonDefaultParamSpecs.map { CodeBlock.of("%1N = %1N", it) })
                                    .build()
                            )
                        }
                    }

                    try {
                        if (scope != null) {
                            addProperty(
                                PropertySpec.builder("_scoped", LAZY_MAP)
                                    .addModifiers(KModifier.OVERRIDE)
                                    .initializer("%T()", LAZY_MAP)
                                    .build()
                            )
                        }

                        val results = resolver.resolveAll(context, astClass).apply {
                            if (options.dumpGraph) {
                                messenger.warn(dumpGraph(astClass, this))
                            }
                        }.optimize(context)

                        with(TypeResultGenerator(options)) {
                            for (result in results) {
                                result.generateInto(this@apply)
                            }
                        }
                    } catch (e: FailedToGenerateException) {
                        error(e.message.orEmpty(), e.element)
                        // Create a stub component to prevent extra compile errors,
                        // the original one will still be reported.
                    }
                }.build()
        }
    }

    private fun collectTypes(
        astClass: AstClass,
        injectName: String,
    ): Context {
        val types = typeCollector.collect(astClass)
        val elementScopeClass = types.scopeClass
        val scopeFromParent = elementScopeClass != astClass
        return Context(
            provider = provider,
            className = injectName,
            types = types,
            scopeComponent = elementScopeClass,
            scopeInterface = if (scopeFromParent) elementScopeClass else null,
            nameAllocator = NameAllocator().apply {
                // don't conflict with properties
                for (prop in astClass.methods) {
                    if (prop is AstProperty) {
                        newName(prop.name)
                    }
                }
            }
        )
    }
}

fun AstAnnotated.scope(options: Options): AstAnnotation? {
    return scopes(options).firstOrNull()
}

fun AstAnnotated.scopes(options: Options): Sequence<AstAnnotation> {
    val scopeAnnotations = annotationsAnnotatedWith(SCOPE.packageName, SCOPE.simpleName).map { annotation ->
        annotation
    }

    if (options.enableJavaxAnnotations) {
        return annotationsAnnotatedWith(JAVAX_SCOPE.packageName, JAVAX_SCOPE.simpleName).map { annotation ->
            annotation
        } + scopeAnnotations
    }
    return scopeAnnotations
}

fun AstAnnotated.isComponent() = hasAnnotation(COMPONENT)

fun AstMember.isProvides() = hasAnnotation(PROVIDES)

fun AstAnnotated.isInject() = hasAnnotation(INJECT)

fun AstAnnotated.isAssistedFactory() = hasAnnotation(ASSISTED_FACTORY)

fun AstAnnotated.assistedFactoryFunctionName() =
    annotation(ASSISTED_FACTORY.packageName, ASSISTED_FACTORY.simpleName)
        ?.argument(ASSISTED_FACTORY_FUNCTION_ARG) as? String

fun AstClass.findInjectConstructors(messenger: Messenger, options: Options): AstConstructor? {
    val injectCtors = constructors.filter {
        if (options.enableJavaxAnnotations) {
            it.hasAnnotation(JAVAX_INJECT) || it.isInject()
        } else {
            it.isInject()
        }
    }.toList()

    val isInject = isInject()
    return when {
        isInject && injectCtors.isNotEmpty() -> {
            messenger.error("Cannot annotate constructor with @Inject in an @Inject-annotated class", this)
            null
        }

        isInject -> primaryConstructor
        injectCtors.size > 1 -> {
            messenger.error("Class cannot contain multiple @Inject-annotated constructors", this)
            null
        }

        injectCtors.isNotEmpty() -> injectCtors.first()
        else -> null
    }
}

/**
 * Finds a function to be generated for the assisted factory
 * @throws FailedToGenerateException there are too many or none functions to generate
 */
fun findAssistedFactoryFunction(astClass: AstClass): AstFunction {
    if (!astClass.isInterface) {
        throw FailedToGenerateException("Assisted factory $astClass must be an interface.", astClass)
    }
    val abstractMethods = astClass.allMethods.filter { it.isAbstract }
    if (abstractMethods.size > 1) {
        throw FailedToGenerateException(
            "Assisted factory $astClass has too many abstract functions: ${abstractMethods.map { it.name }}.",
            astClass
        )
    }
    val factoryFunction = abstractMethods.firstOrNull() as? AstFunction
        ?: throw FailedToGenerateException("Assisted factory $astClass doesn't have any functions.", astClass)

    if (factoryFunction.receiverParameterType != null) {
        throw FailedToGenerateException(
            "Assisted factory $astClass function ${factoryFunction.name} has a receiver parameter. " +
                "Receiver parameters are not supported in assisted factory.",
            astClass
        )
    }

    return factoryFunction
}

/**
 * Finds a function specified in injectFunction parameter of @AssistedFactory annotation
 * @return null if function not specified
 * @throws FailedToGenerateException if function is specified, but not found or doesn't match requirements
 */
fun findAssistedFactoryInjectFunction(
    context: Context,
    key: TypeKey,
    astClass: AstClass,
    factoryFunction: AstFunction,
): AstFunction? {
    val injectedFunctionName = astClass.assistedFactoryFunctionName()
    if (injectedFunctionName.isNullOrBlank()) return null

    val functionName = injectedFunctionName.substringAfterLast(".")
    val functionPackage = injectedFunctionName.substringBeforeLast(".", key.type.packageName)
    val functions = context.provider.findFunctions(functionPackage, functionName)
        .toList()
    val function = if (functions.isEmpty()) {
        throw FailedToGenerateException("Inject function not found for Assisted factory $astClass.", astClass)
    } else if (functions.size == 1) {
        functions.first()
    } else {
        val injectFunctions = functions.filter { it.isInject() }
        if (injectFunctions.isEmpty()) {
            throw FailedToGenerateException(
                "Inject function not found for Assisted factory $astClass.",
                astClass
            )
        } else if (injectFunctions.size > 1) {
            throw FailedToGenerateException(
                "Too many candidates found for inject function for Assisted factory $astClass.",
                astClass
            )
        } else {
            injectFunctions.first()
        }
    }
    if (function.receiverParameterType != null) {
        throw FailedToGenerateException(
            "Cannot inject function with receiver parameter in Assisted factory $astClass.",
            astClass
        )
    }
    if (function.returnType != factoryFunction.returnType) {
        throw FailedToGenerateException(
            "Return type of inject function doesn't match assisted factory's return type $astClass.",
            astClass
        )
    }
    if (!function.isTopLevel) {
        throw FailedToGenerateException(
            "Only top level functions can be injected in assisted factory: $astClass.",
            astClass
        )
    }
    return function
}

fun <E> qualifier(
    provider: AstProvider,
    options: Options,
    element: E?,
    type: AstType,
): AstAnnotation? where E : AstElement, E : AstAnnotated {
    // check for qualifiers incorrectly applied to type arguments
    fun checkTypeArgs(packageName: String, simpleName: String, type: AstType) {
        @Suppress("SwallowedException")
        val arguments = try {
            type.arguments
        } catch (e: IllegalStateException) {
            // We do a deep analysis of all types, but not all types are necessarily resolvable, e.g.
            // this may happen with star projections. In this case stop checking type arguments.
            return
        }

        for (typeArg in arguments) {
            val argQualifier = typeArg.annotationAnnotatedWith(packageName, simpleName)
            if (argQualifier != null) {
                provider.error("Qualifier: $argQualifier can only be applied to the outer type", typeArg)
            }
            checkTypeArgs(packageName, simpleName, typeArg)
        }
    }

    fun qualifier(
        packageName: String,
        simpleName: String,
        provider: AstProvider,
        element: E?,
        type: AstType,
    ): AstAnnotation? {
        val qualifiers = (
            element?.annotationsAnnotatedWith(packageName, simpleName).orEmpty() +
                type.annotationsAnnotatedWith(packageName, simpleName)
            ).toList()
        if (qualifiers.size > 1) {
            provider.error("Cannot apply multiple qualifiers: $qualifiers", element)
        }
        checkTypeArgs(packageName, simpleName, type)
        return qualifiers.firstOrNull()
    }
    // check our qualifier annotation first, then check the javax qualifier annotation. This allows you to have both
    // in case your in the middle of a migration.
    val qualifier = qualifier(
        QUALIFIER.packageName,
        QUALIFIER.simpleName,
        provider,
        element,
        type,
    )
    if (qualifier != null) return qualifier
    return if (options.enableJavaxAnnotations) {
        qualifier(
            JAVAX_QUALIFIER.packageName,
            JAVAX_QUALIFIER.simpleName,
            provider,
            element,
            type,
        )
    } else {
        null
    }
}

fun AstMember.isProvider(): Boolean =
    isAbstract && when (this) {
        is AstFunction -> parameters.isEmpty()
        is AstProperty -> true
    } && receiverParameterType == null && returnType.isNotUnit()

fun AstClass.toInjectName(): String =
    "Inject${toClassName().simpleNames.joinToString("_")}"

fun AstType.toVariableName(): String =
    simpleName.split(".")
        .joinToString("_") { it.replaceFirstChar(Char::lowercase) } +
        joinArgumentTypeNames()

fun AstAnnotated.optInAnnotation(): AnnotationSpec? =
    annotation(OPT_IN.packageName, OPT_IN.simpleName)?.toAnnotationSpec()

fun createSuppressAnnotation(reason: String): AnnotationSpec =
    AnnotationSpec.builder(Suppress::class)
        .addMember("%S", reason)
        .build()

private fun AstType.joinArgumentTypeNames(): String = when {
    arguments.isEmpty() -> ""
    else -> arguments.joinToString(separator = "") {
        it
            .simpleName
            .split(".")
            .joinToString("_") +
            it.joinArgumentTypeNames()
    }
}

private fun dumpGraph(astClass: AstClass, entries: List<TypeResult.Provider>): String {
    val out = StringBuilder(astClass.name).append("\n")
    for (entry in entries) {
        out.append("* ${entry.name}: ${entry.returnType}\n")
        val seen = mutableSetOf<TypeResult>()
        out.renderTree(entry.result) { ref ->
            val walkChildren = ref.result !in seen
            seen.add(ref.result)
            out.apply {
                append(ref.result::class.simpleName)
                append("@")
                append(System.identityHashCode(ref.result))
                append(": ")
                append(ref.key)
                if (!walkChildren) {
                    append(" *")
                }
            }
            if (walkChildren) {
                ref.result.children
            } else {
                EmptyIterator
            }
        }
    }
    return out.toString()
}
