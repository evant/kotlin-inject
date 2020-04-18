package me.tatarka.inject.compiler

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.compiler.ast.AstClass
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class ScopedInjectCompiler : BaseInjectCompiler() {

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        for (element in env.getElementsAnnotatedWith(Component::class.java)) {
            if (element !is TypeElement) continue
            // Only deal with scoped components
            val scopeType = element.scopeType() ?: continue
            val scopedInjects = scopedInjects(scopeType, env)

            val generator = InjectGenerator(this, generateCompanionExtensions)

            try {
                val astClass = element.toAstClass()
                val file = generator.generate(astClass, scopedInjects)
                file.writeTo(filer)
            } catch (e: FailedToGenerateException) {
                // Continue so we can see all errors
                continue
            }
        }
        return false
    }

    private fun scopedInjects(scopeType: TypeElement, env: RoundEnvironment): List<AstClass> {
        return env.getElementsAnnotatedWith(scopeType).mapNotNull {
            // skip component itself, we only want @Inject's annotated with the scope
            if (it.getAnnotation(Component::class.java) != null) {
                null
            } else {
                (it as? TypeElement)?.toAstClass()
            }
        }
    }

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(
        Component::class.java.canonicalName,
        Inject::class.java.canonicalName
    )
}