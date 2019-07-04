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
            process(element as TypeElement)
        }
        return false
    }

    private fun process(element: TypeElement) {

        val props = mutableListOf<PropertySpec>()

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
            if (method.isProvider()) {
                val returnType = typeUtils.asElement(method.returnType)
                if (returnType.getAnnotation(Singleton::class.java) != null) {
                    singletons.add(method.returnType)
                }
            }
        }

        for (singleton in singletons) {
            val codeBlock = CodeBlock.builder()
            codeBlock.add("lazy { ")
            codeBlock.add(provide(TypeKey(singleton), Context(provides = providesMap, binds = bindsMap)))
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
                try {
                    codeBlock.add(
                        provide(
                            TypeKey(method.returnType, qualifier),
                            Context(source = method, provides = providesMap, binds = bindsMap, singletons = singletons)
                        )
                    )
                } catch (e: FailedToGenerateException) {
                    continue
                }

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

        val injectModule = TypeSpec.classBuilder("Inject${element.simpleName}")
            .superclass(element.asClassName())
            .addProperties(props)
            .build()

        val injectFile = FileSpec.builder(element.enclosingElement.toString(), "Inject${element.simpleName}")
            .addType(injectModule)
            .build()

        val createFile = FileSpec.builder("me.tatarka.inject", "Create${element.simpleName}")
            .addFunction(
                FunSpec.builder("createModule")
                    .addModifiers(KModifier.INLINE)
                    .receiver(KClass::class.asClassName().plusParameter(element.asType().asTypeName()))
                    .returns(element.asType().asTypeName())
                    .addCode(CodeBlock.of("return %L.%N()", element.enclosingElement.toString(), injectModule))
                    .build()
            )
            .build()

        val out = File(generatedSourcesRoot).also { it.mkdir() }

        injectFile.writeTo(out)
        createFile.writeTo(out)
    }

    private fun provide(
        key: TypeKey,
        context: Context
    ): CodeBlock {
        if (key.type in context.singletons) {
            return CodeBlock.of("%N", typeUtils.asElement(key.type).simpleName.asSingleton())
        }
        val providesElement = context.provides[key]
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
        providesElement: ExecutableElement,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        codeBlock.add("%N(", providesElement.simpleName)
        providesElement.parameters.forEachIndexed { i, param ->
            if (i != 0) {
                codeBlock.add(",")
            }
            codeBlock.add(provide(TypeKey(param.asType()), context))
        }
        codeBlock.add(")")
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
        val constructor = ElementFilter.constructorsIn(element.enclosedElements)[0]
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

    private fun ExecutableElement.isProvider(): Boolean = modifiers.contains(Modifier.ABSTRACT) && parameters.isEmpty()

    private fun Element.qualifier(): Any? =
        annotationMirrors.find {
            it.annotationType.asElement().getAnnotation(Qualifier::class.java) != null
        }?.wrap()

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(Module::class.java.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    data class Context(
        val source: Element? = null,
        val provides: Map<TypeKey, ExecutableElement>,
        val binds: Map<TypeKey, ExecutableElement>,
        val singletons: List<TypeMirror> = emptyList()
    )

    data class TypeKey(val type: TypeMirror, val qualifier: Any? = null)

    private fun AnnotationMirror.wrap(): AnnotationMirrorWrapper =
        AnnotationMirrorWrapper(annotationType.asTypeName(), elementValues.values.toList().map { it.value })

    data class AnnotationMirrorWrapper(val type: TypeName, val values: List<Any>)

    private class FailedToGenerateException : Exception()
}
