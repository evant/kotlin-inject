package me.tatarka.inject.compiler.kapt

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.compiler.AstClass
import me.tatarka.inject.compiler.FailedToGenerateException
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.scopeClass
import me.tatarka.inject.compiler.scopeType
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class InjectCompiler : BaseInjectCompiler() {

    private val annotationNames = mutableSetOf<String>()

    init {
        annotationNames.add(Component::class.java.canonicalName)
    }

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        val generator = InjectGenerator(this, options)
        val allScopedClasses = mutableSetOf<AstClass>()

        for (element in env.getElementsAnnotatedWith(Component::class.java)) {
            if (element !is TypeElement) continue

            val astClass = element.toAstClass()
            val scopeClass = astClass.scopeClass(messenger, options)
            if (scopeClass != null) {
                val scopeType = scopeClass.scopeType(options)!!
                annotationNames.add(scopeType.toString())
                val scopedClasses = scopedClasses(scopeType, env)
                allScopedClasses.addAll(scopedClasses)
            }

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

    private fun scopedClasses(scopeType: AstClass, env: RoundEnvironment): List<AstClass> {
        return env.getElementsAnnotatedWith(scopeType.element).mapNotNull {
            (it as? TypeElement)?.toAstClass()
        }
    }

    override fun getSupportedAnnotationTypes(): Set<String> = annotationNames
}
