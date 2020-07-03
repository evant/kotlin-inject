package me.tatarka.inject.compiler

import me.tatarka.inject.annotations.Component
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class InjectCompiler : BaseInjectCompiler() {

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        for (element in env.getElementsAnnotatedWith(Component::class.java)) {
            if (element !is TypeElement) continue
            val scopeType = element.scopeType()
            // Only deal with non-scoped components
            if (scopeType != null) continue

            val generator = InjectGenerator(this, options)

            try {
                val astClass = element.toAstClass()
                val file = generator.generate(astClass)
                file.writeTo(filer)
            } catch (e: FailedToGenerateException) {
                error(e.message.orEmpty(), e.element)
                // Continue so we can see all errors
                continue
            }
        }
        return false
    }

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(
        Component::class.java.canonicalName
    )
}
