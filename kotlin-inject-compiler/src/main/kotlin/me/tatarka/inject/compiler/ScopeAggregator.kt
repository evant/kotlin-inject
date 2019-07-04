package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Types
import javax.tools.Diagnostic

private const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

class ScopeAggregator : AbstractProcessor() {

    private lateinit var generatedSourcesRoot: String
    private lateinit var typeUtils: Types
    private lateinit var messager: Messager

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        this.generatedSourcesRoot = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]!!
        this.typeUtils = processingEnv.typeUtils
        this.messager = processingEnv.messager
    }

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        for (element in elements) {
            val scope = element.annotationMirrors.find {
                it.annotationType.toString() == "me.tatarka.inject.annotations.Scope"
            }
            if (scope != null) {
                val file =
                    FileSpec.builder(element.enclosingElement.toString(), "Inject${element.simpleName}")
                        .addType(TypeSpec.classBuilder("Inject${element.simpleName}")
                            .apply {
                                for (e in env.getElementsAnnotatedWith(element)) {
                                    if (e is ExecutableElement) {
                                        addProperty(
                                            PropertySpec.builder(
                                                e.simpleName.asScopedProp(),
                                                ClassName.bestGuess("me.tatarka.inject.Property").plusParameter(
                                                    e.returnType.asTypeName()
                                                )
                                            ).initializer(CodeBlock.of("Property()"))
                                                .build()
                                        )
                                    }
                                }
                            }
                            .build())
                        .build()

                val out = File(generatedSourcesRoot).also { it.mkdir() }

                file.writeTo(out)
            }
        }
        return false
    }

    override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()
}