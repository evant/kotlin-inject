package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

private const val ANNOTATION_PACKAGE_NAME = "me.tatarka.inject.annotations"
val COMPONENT = ClassName(ANNOTATION_PACKAGE_NAME, "Component")
val PROVIDES = ClassName(ANNOTATION_PACKAGE_NAME, "Provides")
val SCOPE = ClassName(ANNOTATION_PACKAGE_NAME, "Scope")
val INJECT = ClassName(ANNOTATION_PACKAGE_NAME, "Inject")
val INTO_MAP = ClassName(ANNOTATION_PACKAGE_NAME, "IntoMap")
val INTO_SET = ClassName(ANNOTATION_PACKAGE_NAME, "IntoSet")

val JAVAX_SCOPE = ClassName("javax.inject", "Scope")
val JAVAX_INJECT = ClassName("javax.inject", "Inject")
val JAVAX_QUALIFIER = ClassName("javax.inject", "Qualifier")

val SCOPED_COMPONENT = ClassName("me.tatarka.inject.internal", "ScopedComponent")
val LAZY_MAP = ClassName("me.tatarka.inject.internal", "LazyMap")

class InjectGenerator<Output, Provider>(
    private val provider: Provider,
    private val options: Options,
) : AstProvider by provider
        where Provider : AstProvider, Provider : OutputProvider<Output> {

    private val createGenerator = CreateGenerator(provider, options)

    var scopeType: AstType? = null
        private set

    fun generate(astClass: AstClass): AstFileSpec<Output> {
        if (!astClass.isAbstract) {
            throw FailedToGenerateException("@Component class: $astClass must be abstract", astClass)
        } else if (astClass.visibility == AstVisibility.PRIVATE) {
            throw FailedToGenerateException("@Component class: $astClass must not be private", astClass)
        }

        val constructor = astClass.primaryConstructor

        val injectName = astClass.toInjectName()
        val injectComponent = generateInjectComponent(astClass, injectName, constructor)
        val createFunction = createGenerator.create(astClass, constructor, injectComponent.typeSpec)

        return provider.astFileSpec(
            FileSpec.builder(astClass.packageName, injectName)
                .apply {
                    createFunction.forEach { addFunction(it) }
                }, injectComponent
        )
    }

    @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth")
    private fun generateInjectComponent(
        astClass: AstClass,
        injectName: String,
        constructor: AstConstructor?
    ): AstTypeSpec {
        val context = collectTypes(astClass, injectName)
        val resolver = TypeResultResolver(this, options)
        val scope = context.collector.scopeClass
        scopeType = scope?.scopeType(options)

        return provider.astTypeSpec(
            TypeSpec.classBuilder(context.className)
                .apply {
                    if (astClass.isInterface) {
                        addSuperinterface(astClass.asClassName())
                    } else {
                        superclass(astClass.asClassName())
                    }
                    if (scope != null) {
                        addSuperinterface(SCOPED_COMPONENT)
                    }
                    addModifiers(astClass.visibility.toModifier())
                    if (constructor != null) {
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
                        val paramSpecs = params.map { it.asParameterSpec() }
                        val nonDefaultParamSpecs =
                            constructor.parameters.filter { !it.hasDefault }.map { it.asParameterSpec() }
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

                        for (result in results) {
                            result.generateInto(this)
                        }
                    } catch (e: FailedToGenerateException) {
                        error(e.message.orEmpty(), e.element)
                        // Create a stub component to prevent extra compile errors,
                        // the original one will still be reported.
                    }
                }, astClass
        )
    }

    private fun collectTypes(
        astClass: AstClass,
        injectName: String,
    ): Context {
        val typeCollector = TypeCollector(
            provider = this,
            options = options,
            astClass = astClass,
        )
        val elementScopeClass = typeCollector.scopeClass
        val scopeFromParent = elementScopeClass != astClass
        return Context(
            provider = this,
            className = injectName,
            collector = typeCollector,
            scopeInterface = if (scopeFromParent) elementScopeClass else null,
        )
    }
}

fun AstElement.scopeType(options: Options): AstType? {
    if (options.enableJavaxAnnotations) {
        val annotation = annotationAnnotatedWith(JAVAX_SCOPE.packageName, JAVAX_SCOPE.simpleName)
        if (annotation != null) {
            return annotation.type
        }
    }
    return annotationAnnotatedWith(SCOPE.packageName, SCOPE.simpleName)?.type
}

fun AstElement.isComponent() = hasAnnotation(COMPONENT.packageName, COMPONENT.simpleName)

fun AstMethod.isProvides() = hasAnnotation(PROVIDES.packageName, PROVIDES.simpleName)

fun AstElement.isInject() = hasAnnotation(INJECT.packageName, INJECT.simpleName)

fun AstClass.findInjectConstructors(messenger: Messenger, options: Options): AstConstructor? {
    val injectCtors = constructors.filter {
        if (options.enableJavaxAnnotations) {
            it.hasAnnotation(JAVAX_INJECT.packageName, JAVAX_INJECT.simpleName) || it.isInject()
        } else {
            it.isInject()
        }
    }

    return when {
        isInject() && injectCtors.isNotEmpty() -> {
            messenger.error("Cannot annotate constructor with @Inject in an @Inject-annotated class", this)
            null
        }
        isInject() -> primaryConstructor
        injectCtors.size > 1 -> {
            messenger.error("Class cannot contain multiple @Inject-annotated constructors", this)
            null
        }
        injectCtors.isNotEmpty() -> injectCtors.first()
        else -> null
    }
}

fun AstElement.qualifier(options: Options): AstAnnotation? {
    return if (options.enableJavaxAnnotations) {
        annotationAnnotatedWith(JAVAX_QUALIFIER.packageName, JAVAX_QUALIFIER.simpleName)
    } else {
        null
    }
}

fun AstMethod.isProvider(): Boolean =
    !isProvides() && isAbstract && when (this) {
        is AstFunction -> parameters.isEmpty()
        is AstProperty -> true
    } && receiverParameterType == null && returnType.isNotUnit()

fun AstClass.toInjectName(): String =
    "Inject${asClassName().simpleNames.joinToString("_")}"

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