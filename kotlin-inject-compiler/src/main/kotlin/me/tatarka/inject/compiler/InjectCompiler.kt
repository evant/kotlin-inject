package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import me.tatarka.inject.annotations.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.reflect.KClass

private const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

class InjectCompiler : AbstractProcessor() {

    private lateinit var generatedSourcesRoot: String
    private lateinit var filer: Filer
    private lateinit var typeUtils: Types
    private lateinit var messager: Messager

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        this.generatedSourcesRoot = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]!!
        this.filer = processingEnv.filer
        this.typeUtils = processingEnv.typeUtils
        this.messager = processingEnv.messager
    }

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        for (element in env.getElementsAnnotatedWith(Module::class.java)) {
            try {
                process(element as TypeElement)
            } catch (e: FailedToGenerateException) {
                // Continue so we can see all errors
                continue
            }
        }
        return false
    }

    private fun process(element: TypeElement) {

        val context = collectTypes(element)

        val props = mutableListOf<PropertySpec>()

        for (method in context.binds.values) {
            props.add(
                PropertySpec.builder(
                    method.simpleName.asProp(),
                    method.returnType.asTypeName(),
                    KModifier.OVERRIDE
                ).receiver(method.parameters[0].asType().asTypeName())
                    .getter(FunSpec.getterBuilder().addCode("return this").build())
                    .build()
            )
        }

        for (singleton in context.singletons) {
            val codeBlock = CodeBlock.builder()
            codeBlock.add("lazy { ")
            codeBlock.add(provide(TypeKey(singleton), context.withoutSingletons()))
            codeBlock.add(" }")
            props.add(
                PropertySpec.builder(
                    typeUtils.asElement(singleton).simpleName.asSingleton(),
                    singleton.asTypeName(),
                    KModifier.PRIVATE
                ).delegate(codeBlock.build())
                    .build()
            )
        }

        for (method in ElementFilter.methodsIn(element.enclosedElements)) {
            if (method.isProvider()) {
                val qualifier = method.qualifier()

                val codeBlock = CodeBlock.builder()
                codeBlock.add("return ")
                codeBlock.add(provide(TypeKey(method.returnType, qualifier), context))
                props.add(
                    PropertySpec.builder(
                        method.simpleName.asProp(),
                        method.returnType.asTypeName(),
                        KModifier.OVERRIDE
                    )
                        .getter(
                            FunSpec.getterBuilder()
                                .addCode(codeBlock.build())
                                .build()
                        )
                        .build()
                )
            }
        }

        val constructor = element.constructor()

        val injectModule = TypeSpec.classBuilder("Inject${element.simpleName}")
            .addModifiers(KModifier.PRIVATE)
            .superclass(element.asClassName())
            .apply {
                if (constructor != null) {
                    val funSpec = FunSpec.constructorBuilder()
                    for (parameter in constructor.parameters) {
                        val p = ParameterSpec.get(parameter)
                        funSpec.addParameter(p)
                        addSuperclassConstructorParameter("%N", p)
                    }
                    primaryConstructor(funSpec.build())
                }
            }
            .addProperties(props)
            .build()

        val companion = element.getCompanion()

        val file = FileSpec.builder(element.enclosingElement.toString(), "Inject${element.simpleName}")
            .addType(injectModule)
            .addFunction(
                FunSpec.builder("create")
                    .apply {
                        if (constructor != null) {
                            addParameters(ParameterSpec.parametersOf(constructor))
                        }
                        if (companion != null) {
                            receiver(companion.asType().asTypeName())
                        } else {
                            receiver(KClass::class.asClassName().plusParameter(element.asType().asTypeName()))
                        }
                    }
                    .returns(element.asType().asTypeName())
                    .apply {
                        val codeBlock = CodeBlock.builder()
                        codeBlock.add("return %L.%N(", element.enclosingElement.toString(), injectModule)
                        if (constructor != null) {
                            constructor.parameters.forEachIndexed { i, parameter ->
                                if (i != 0) {
                                    codeBlock.add(", ")
                                }
                                codeBlock.add("%L", parameter.simpleName.toString())
                            }
                        }
                        codeBlock.add(")")
                        addCode(codeBlock.build())
                    }
                    .build()
            )
            .build()

        val out = File(generatedSourcesRoot).also { it.mkdir() }

        file.writeTo(out)
    }

    private fun collectTypes(element: Element, name: String? = null): Context {

        val providesMap = mutableMapOf<TypeKey, ExecutableElement>()
        val bindsMap = mutableMapOf<TypeKey, ExecutableElement>()
        val singletons = mutableListOf<TypeMirror>()

        for (method in ElementFilter.methodsIn(element.enclosedElements)) {
            if (method.getAnnotation(Provides::class.java) != null) {
                providesMap[TypeKey(method.returnType, method.qualifier())] = method

                if (method.getAnnotation(Singleton::class.java) != null) {
                    singletons.add(method.returnType)
                }
            }
            if (method.getAnnotation(Binds::class.java) != null) {
                messager.printMessage(Diagnostic.Kind.WARNING, "e:${method.simpleName}")
                bindsMap[TypeKey(method.returnType, method.qualifier())] = method
            }
            if (method.isProvider()) {
                val returnType = typeUtils.asElement(method.returnType)
                if (returnType.getAnnotation(Singleton::class.java) != null) {
                    singletons.add(method.returnType)
                }
            }
        }

        val parents = mutableListOf<Context>()

        val constructor = element.constructor()
        if (constructor != null) {
            for (parameter in constructor.parameters) {
                val elem = typeUtils.asElement(parameter.asType())
                if (elem.isModule()) {
                    parents.add(collectTypes(elem, name = parameter.simpleName.toString()))
                }
            }
        }
        return Context(
            source = element,
            name = name,
            parents = parents,
            provides = providesMap,
            binds = bindsMap,
            singletons = singletons
        )
    }

    private fun provide(
        key: TypeKey,
        context: Context
    ): CodeBlock {
        if (key.type in context.singletons) {
            return CodeBlock.of("%N", typeUtils.asElement(key.type).simpleName.asSingleton())
        }
        val providesElement = context.findProvides(key)
        return if (providesElement != null) {
            provideProvides(providesElement, context)
        } else {
            val bindsElement = context.binds[key]
            if (bindsElement != null) {
                provideBinds(bindsElement, context)
            } else {
                val element = typeUtils.asElement(key.type)
                if (element.getAnnotation(Inject::class.java) != null) {
                    provideConstructor(element, context)
                } else {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Cannot find an @Inject constructor, @Provides, or @Binds for class: $element on ${context.source}",
                        context.source
                    )
                    throw FailedToGenerateException()
                }
            }
        }
    }

    private fun provideProvides(
        providesElement: Result,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        if (providesElement.name != null) {
            codeBlock.add("%L.", providesElement.name)
        }
        if (providesElement.element.isProp()) {
            codeBlock.add("%N", providesElement.element.simpleName.asProp())
        } else {
            codeBlock.add("%N(", providesElement.element.simpleName)
            providesElement.element.parameters.forEachIndexed { i, param ->
                if (i != 0) {
                    codeBlock.add(",")
                }
                codeBlock.add(provide(TypeKey(param.asType()), context))
            }
            codeBlock.add(")")
        }
        return codeBlock.build()
    }

    private fun provideBinds(
        bindsElement: ExecutableElement,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        val concreate = bindsElement.parameters[0]
        codeBlock.add(provide(TypeKey(concreate.asType(), concreate.qualifier()), context))
        codeBlock.add(".%N", bindsElement.simpleName.asProp())
        return codeBlock.build()
    }

    private fun provideConstructor(
        element: Element,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        codeBlock.add("%T(", element.asType().asTypeName())
        val constructor = element.constructor()!!
        constructor.parameters.forEachIndexed { i, param ->
            if (i != 0) {
                codeBlock.add(",")
            }
            codeBlock.add(provide(TypeKey(param.asType(), param.qualifier()), context))
        }
        codeBlock.add(")")
        return codeBlock.build()
    }

    private fun Name.asProp(): String = toString().removePrefix("get").decapitalize()

    private fun Name.asSingleton(): String = "_" + toString().decapitalize()

    private fun Element.isModule() = getAnnotation(Module::class.java) != null

    private fun ExecutableElement.isProvider(): Boolean = modifiers.contains(Modifier.ABSTRACT) && parameters.isEmpty()

    private fun ExecutableElement.isProp(): Boolean = simpleName.startsWith("get") && parameters.isEmpty()

    private fun Element.getCompanion(): TypeElement? = ElementFilter.typesIn(enclosedElements).firstOrNull()

    private fun Element.constructor(): ExecutableElement? = ElementFilter.constructorsIn(enclosedElements).firstOrNull()

    private fun Element.qualifier(): Any? =
        annotationMirrors.find {
            it.annotationType.asElement().getAnnotation(Qualifier::class.java) != null
        }?.wrap()


    override fun getSupportedAnnotationTypes(): Set<String> = setOf(Module::class.java.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    data class Context(
        val source: Element? = null,
        val name: String? = null,
        val parents: List<Context>,
        val provides: Map<TypeKey, ExecutableElement>,
        val binds: Map<TypeKey, ExecutableElement>,
        val singletons: List<TypeMirror> = emptyList()
    ) {
        fun withoutSingletons() = copy(singletons = emptyList())

        fun findProvides(key: TypeKey): Result? {
            val result = provides[key]
            if (result != null) {
                return Result(name, result)
            }
            for (parent in parents) {
                val parentResult = parent.findProvides(key)
                if (parentResult != null) {
                    return parentResult
                }
            }
            return null;
        }
    }

    data class Result(val name: String?, val element: ExecutableElement)

    data class TypeKey(val type: TypeMirror, val qualifier: Any? = null)

    private fun AnnotationMirror.wrap(): AnnotationMirrorWrapper =
        AnnotationMirrorWrapper(annotationType.asTypeName(), elementValues.values.toList().map { it.value })

    data class AnnotationMirrorWrapper(val type: TypeName, val values: List<Any>)

    private class FailedToGenerateException : Exception()
}
