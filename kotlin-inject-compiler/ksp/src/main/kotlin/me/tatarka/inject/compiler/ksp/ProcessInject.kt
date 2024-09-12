package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Visibility
import com.google.devtools.ksp.visitor.KSValidateVisitor
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.compiler.FailedToGenerateException
import me.tatarka.inject.compiler.InjectGenerator
import me.tatarka.inject.compiler.PROVIDES
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.KSAstProvider

internal fun processInject(
    element: KSClassDeclaration,
    provider: KSAstProvider,
    codeGenerator: CodeGenerator,
    injectGenerator: InjectGenerator,
    skipValidation: Boolean = false,
): Boolean = with(provider) {
    val astClass = element.toAstClass()
    if (skipValidation || validate(element)) {
        process(astClass, provider, codeGenerator, injectGenerator)
        true
    } else {
        false
    }
}

private fun validate(declaration: KSClassDeclaration): Boolean {
    return declaration.accept(
        object : KSValidateVisitor({ node, data ->
            when (node) {
                is KSFunctionDeclaration ->
                    node.getVisibility() != Visibility.PRIVATE &&
                        (node.isAbstract || node.hasAnnotation(PROVIDES.packageName, PROVIDES.simpleName))

                is KSPropertyDeclaration ->
                    node.getVisibility() != Visibility.PRIVATE &&
                        (
                            node.isAbstract() ||
                                node.getter?.hasAnnotation(
                                    PROVIDES.packageName,
                                    PROVIDES.simpleName
                                ) ?: true
                            )

                // skip validating inner classes/companion objects as they aren't scanned for types
                is KSClassDeclaration -> node == data
                else -> true
            }
        }) {
            override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: KSNode?): Boolean {
                if (!super.visitClassDeclaration(classDeclaration, data)) {
                    return false
                }
                // also visit parent classes
                if (!classDeclaration.superTypes.all { it.resolve().declaration.accept(this, data) }) {
                    return false
                }
                return true
            }
        },
        null
    )
}

private fun process(
    astClass: AstClass,
    provider: KSAstProvider,
    codeGenerator: CodeGenerator,
    generator: InjectGenerator,
) {
    try {
        val file = generator.generate(astClass)
        file.writeTo(codeGenerator, aggregating = true)
    } catch (e: FailedToGenerateException) {
        provider.error(e.message.orEmpty(), e.element)
        // Continue so we can see all errors
    }
}
