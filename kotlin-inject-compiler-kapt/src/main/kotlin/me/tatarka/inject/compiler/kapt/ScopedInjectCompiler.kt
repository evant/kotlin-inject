package me.tatarka.inject.compiler.kapt

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Scope
import me.tatarka.inject.compiler.*
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class ScopedInjectCompiler : BaseInjectCompiler() {

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        val allScopedClasses = mutableSetOf<AstClass>()
        val generator = InjectGenerator(this, options)

        for (element in env.getElementsAnnotatedWith(Component::class.java)) {
            if (element !is TypeElement) continue
            val astClass = element.toAstClass()
            // Only deal with scoped components
            val scopedClass = astClass.scopeClass(messenger, options) ?: continue

            val scopeType = scopedClass.scopeType(options)!!
            val scopedClasses = scopedClasses(scopeType, env)
            allScopedClasses.addAll(scopedClasses)

            try {
                val file = generator.generate(astClass, scopedClasses)
                file.writeTo(filer)
            } catch (e: FailedToGenerateException) {
                error(e.message.orEmpty(), e.element)
                // Continue so we can see all errors
                continue
            }
        }

        try {
            for (file in generator.generateScopedInterfaces(allScopedClasses)) {
                file.writeTo(filer)
            }
        } catch (e: FailedToGenerateException) {
            error(e.message.orEmpty(), e.element)
            // Continue so we can see all errors
        }

        return false
    }

    private fun scopedClasses(scopeType: AstClass, env: RoundEnvironment): List<AstClass> {
        return env.getElementsAnnotatedWith(scopeType.element).mapNotNull {
            (it as? TypeElement)?.toAstClass()
        }
    }

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(
        Component::class.java.canonicalName,
        Inject::class.java.canonicalName
    )
}