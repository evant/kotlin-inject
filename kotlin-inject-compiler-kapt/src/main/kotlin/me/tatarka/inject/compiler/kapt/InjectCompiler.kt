package me.tatarka.inject.compiler.kapt

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.compiler.FailedToGenerateException
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.scopeClass
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class InjectCompiler : BaseInjectCompiler() {

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        val generator = InjectGenerator(this, options)

        for (element in env.getElementsAnnotatedWith(Component::class.java)) {
            if (element !is TypeElement) continue

            val astClass = element.toAstClass()
            val scopeClass = astClass.scopeClass(messenger)
            // Only deal with non-scoped components
            if (scopeClass != null) continue

            try {
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
