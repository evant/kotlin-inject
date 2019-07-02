package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import me.tatarka.inject.annotations.Binds
import me.tatarka.inject.annotations.Module
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Singleton
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
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

        val providesMap = mutableMapOf<TypeMirror, ExecutableElement>()
        val bindsMap = mutableMapOf<TypeMirror, ExecutableElement>()
        val singletons = mutableListOf<TypeMirror>()

        for (method in ElementFilter.methodsIn(element.enclosedElements)) {
            if (method.getAnnotation(Provides::class.java) != null) {
                providesMap[method.returnType] = method

                if (method.getAnnotation(Singleton::class.java) != null) {
                    singletons.add(method.returnType)
                }
            }
            if (method.getAnnotation(Binds::class.java) != null) {
                messager.printMessage(Diagnostic.Kind.WARNING, "e:${method.simpleName}")
                bindsMap[method.returnType] = method

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
        }

        for (singleton in singletons) {
            val codeBlock = CodeBlock.builder()
            codeBlock.add("lazy { ")
            codeBlock.add(provide(singleton, providesMap, bindsMap, emptyList()))
            codeBlock.add(" }")
            props.add(
                PropertySpec.builder(
                    typeUtils.asElement(singleton).simpleName.toString().decapitalize(),
                    singleton.asTypeName(),
                    KModifier.PRIVATE
                ).delegate(codeBlock.build())
                    .build()
            )
        }

        for (method in ElementFilter.methodsIn(element.enclosedElements)) {
            if (method.modifiers.contains(Modifier.ABSTRACT) && method.parameters.isEmpty()) {

                val codeBlock = CodeBlock.builder()
                codeBlock.add("return ")
                codeBlock.add(provide(method.returnType, providesMap, bindsMap, singletons))

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
            .addModifiers(KModifier.PRIVATE)
            .superclass(element.asClassName())
            .addProperties(props)
            .build()

        val file = FileSpec.builder(element.enclosingElement.toString(), "Inject${element.simpleName}")
            .addType(injectModule)
            .addFunction(
                FunSpec.builder("create")
                    .receiver(KClass::class.asClassName().plusParameter(element.asType().asTypeName()))
                    .returns(element.asType().asTypeName())
                    .addCode(CodeBlock.of("return %N()", injectModule))
                    .build()
            )
            .build()
        val out = File(generatedSourcesRoot).also { it.mkdir() }

        file.writeTo(out)
    }

    private fun provide(
        type: TypeMirror,
        providesMap: Map<TypeMirror, ExecutableElement>,
        bindsMap: Map<TypeMirror, ExecutableElement>,
        singletons: List<TypeMirror>
    ): CodeBlock {
        if (type in singletons) {
           return CodeBlock.of("%N", typeUtils.asElement(type).simpleName.toString().decapitalize())
        }
        val providesElement = providesMap[type]
        if (providesElement != null) {
            return provideProvides(providesElement, providesMap, bindsMap, singletons)
        } else {
            val codeBlock = CodeBlock.builder()
            val bindsElement = bindsMap[type]
            if (bindsElement != null) {
                val concreate = bindsElement.parameters[0]
                codeBlock.add(provide(concreate.asType(), providesMap, bindsMap, singletons))
                codeBlock.add(".%N", bindsElement.simpleName.asProp())
            } else {
                codeBlock.add("%T(", type.asTypeName())
                val constructor = ElementFilter.constructorsIn(typeUtils.asElement(type).enclosedElements)[0]
                constructor.parameters.forEachIndexed { i, param ->
                    if (i != 0) {
                        codeBlock.add(",")
                    }
                    codeBlock.add(provide(param.asType(), providesMap, bindsMap, singletons))
                }
                codeBlock.add(")")
            }
            return codeBlock.build()
        }
    }

    private fun provideProvides(
        providesElement: ExecutableElement,
        providesMap: Map<TypeMirror, ExecutableElement>,
        bindsMap: Map<TypeMirror, ExecutableElement>,
        singletons: List<TypeMirror>
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        codeBlock.add("%N(", providesElement.simpleName)
        providesElement.parameters.forEachIndexed { i, param ->
            if (i != 0) {
                codeBlock.add(",")
            }
            codeBlock.add(provide(param.asType(), providesMap, bindsMap, singletons))
        }
        codeBlock.add(")")
        return codeBlock.build()
    }

    private fun Name.asProp(): String = toString().removePrefix("get").decapitalize()

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(Module::class.java.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()
}
