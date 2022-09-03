package me.tatarka.inject.compiler.kapt

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.compiler.FailedToGenerateException
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.Options
import me.tatarka.inject.compiler.Profiler
import me.tatarka.kotlin.ast.ModelAstProvider
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

private const val OPTION_GENERATE_COMPANION_EXTENSIONS = "me.tatarka.inject.generateCompanionExtensions"

class InjectCompiler(private val profiler: Profiler? = null) : AbstractProcessor() {

    private lateinit var env: ProcessingEnvironment
    private lateinit var options: Options
    private lateinit var filer: Filer

    private val annotationNames = mutableSetOf<String>()
    private lateinit var provider: ModelAstProvider
    private lateinit var generator: InjectGenerator

    init {
        annotationNames.add(Component::class.java.canonicalName)
    }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        env = processingEnv
        options = Options.from(processingEnv.options)
        filer = processingEnv.filer
        provider = ModelAstProvider(env)
        generator = InjectGenerator(provider, options)

        processingEnv.messager.printMessage(
            Diagnostic.Kind.WARNING,
            """The kotlin-inject kapt backend is deprecated and will be removed in a future version.
               Please migrate to ksp https://github.com/google/ksp
               You should replace: kapt("me.tatarka.inject:kotlin-inject-compiler-kapt:<version>")
               with:               ksp("me.tatarka.inject:kotlin-inject-compiler-ksp:<version>")
            """.trimIndent()
        )
    }

    @Suppress("LoopWithTooManyJumpStatements")
    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        if (elements.isEmpty()) {
            return false
        }

        profiler?.onStart()

        for (element in env.getElementsAnnotatedWith(Component::class.java)) {
            if (element !is TypeElement) continue

            val astClass = with(provider) { element.toAstClass() }

            try {
                val file = generator.generate(astClass)
                generator.scopeType?.let {
                    annotationNames.add(it.toString())
                }
                file.writeTo(filer)
            } catch (e: FailedToGenerateException) {
                provider.error(e.message.orEmpty(), e.element)
                // Continue so we can see all errors
                continue
            }
        }

        profiler?.onStop()

        return false
    }

    override fun getSupportedAnnotationTypes(): Set<String> = annotationNames
    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()
    override fun getSupportedOptions(): Set<String> = setOf(OPTION_GENERATE_COMPANION_EXTENSIONS)
}