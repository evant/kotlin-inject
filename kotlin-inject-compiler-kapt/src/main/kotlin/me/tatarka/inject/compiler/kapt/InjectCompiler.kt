package me.tatarka.inject.compiler.kapt

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.compiler.*
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class InjectCompiler(profiler: Profiler? = null) : BaseInjectCompiler(profiler) {

    private val annotationNames = mutableSetOf<String>()

    init {
        annotationNames.add(Component::class.java.canonicalName)
    }

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        val generator = InjectGenerator(this, options)

        for (element in env.getElementsAnnotatedWith(Component::class.java)) {
            if (element !is TypeElement) continue

            val astClass = element.toAstClass()

            try {
                val file = generator.generate(astClass)
                generator.scopeType?.let {
                    annotationNames.add(it.toString())
                }
                file.writeTo(filer)
            } catch (e: FailedToGenerateException) {
                error(e.message.orEmpty(), e.element)
                // Continue so we can see all errors
                continue
            }
        }
        return false
    }

    override fun getSupportedAnnotationTypes(): Set<String> = annotationNames
}
