package me.tatarka.inject.android.fragment.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import me.tatarka.inject.compiler.ksp.asClassName
import me.tatarka.inject.compiler.ksp.findAnnotation
import java.util.Locale

class FragmentFactoryProcessor : SymbolProcessor {
    private lateinit var codeGenerator: CodeGenerator
    private lateinit var logger: KSPLogger

    override fun init(
        options: Map<String, String>,
        kotlinVersion: KotlinVersion,
        codeGenerator: CodeGenerator,
        logger: KSPLogger
    ) {
        this.codeGenerator = codeGenerator
        this.logger = logger
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val injectFragments = resolver.getSymbolsWithAnnotation(INJECT.canonicalName).mapNotNull {
            if (it is KSClassDeclaration && it.extendsFragment()) it else null
        }.groupBy { fragment ->
            val intoComponent = fragment.findAnnotation(INTO_COMPONENT.packageName, INTO_COMPONENT.simpleName)
            intoComponent?.arguments?.first { it.name?.asString() == "value" }?.value
        }
        for (element in resolver.getSymbolsWithAnnotation(GENERATE_FRAGMENT_FACTORY.canonicalName)) {
            if (element !is KSDeclaration) continue
            val generateFragmentFactory =
                element.findAnnotation(GENERATE_FRAGMENT_FACTORY.packageName, GENERATE_FRAGMENT_FACTORY.simpleName)!!
            val isDefault =
                generateFragmentFactory.arguments.first { it.name?.asString() == "default" }.value as Boolean
            val key = if (isDefault) null else TODO()
            val fragments = injectFragments[key] ?: emptyList()
            val generatedClassName = "FragmentFactory${element.simpleName.asString()}"
            val file = element.containingFile!!
            val fileSpec = FileSpec.builder(element.packageName.asString(), generatedClassName)
                .addType(
                    TypeSpec.interfaceBuilder(generatedClassName)
                        .apply {
                            if (fragments.isNotEmpty()) {
                                for (fragment in fragments) {
                                    val name = fragment.simpleName.asString().decapitalize(Locale.US)
                                    val type = fragment.asClassName()
                                    addProperty(
                                        PropertySpec.builder(name, FRAGMENT_ENTRY)
                                            .receiver(LambdaTypeName.get(returnType = type))
                                            .getter(
                                                FunSpec.getterBuilder()
                                                    .addAnnotation(PROVIDES)
                                                    .addAnnotation(INTO_MAP)
                                                    .addStatement("return %N(this)", FRAGMENT_ENTRY)
                                                    .build()
                                            ).build()
                                    )
                                    addProperty(name, FRAGMENT_ENTRY)
                                }
                            } else {
                                addProperty(
                                    PropertySpec.builder(
                                        "noFragments", ClassName("kotlin.collections", "Map").parameterizedBy(
                                            ClassName("kotlin", "String"),
                                            LambdaTypeName.get(returnType = FRAGMENT)
                                        )
                                    ).getter(
                                        FunSpec.getterBuilder()
                                            .addAnnotation(PROVIDES)
                                            .addStatement("return emptyMap()")
                                            .build()
                                    ).build()
                                )
                            }
                        }
                        .build()
                ).build()
            codeGenerator.createNewFile(
                Dependencies(true, file),
                fileSpec.packageName,
                fileSpec.name
            ).bufferedWriter().use { fileSpec.writeTo(it) }
        }
        return emptyList()
    }

    private fun KSClassDeclaration.extendsFragment(): Boolean {
        return getAllSuperTypes().any {
            val decl = it.declaration
            decl.packageName.asString() == FRAGMENT.packageName && decl.simpleName.asString() == FRAGMENT.simpleName
        }
    }

    companion object {
        private const val ANNOTATION_PACKAGE_NAME = "me.tatarka.inject.annotations"
        private const val ANDROID_ANNOTATION_PACKAGE_NAME = "me.tatarka.inject.android.annotations"
        val PROVIDES = ClassName(ANNOTATION_PACKAGE_NAME, "Provides")
        val INTO_MAP = ClassName(ANNOTATION_PACKAGE_NAME, "IntoMap")
        val FRAGMENT = ClassName("androidx.fragment.app", "Fragment")
        val INJECT = ClassName(ANDROID_ANNOTATION_PACKAGE_NAME, "Inject")
        val GENERATE_FRAGMENT_FACTORY = ClassName(ANDROID_ANNOTATION_PACKAGE_NAME, "GenerateFragmentFactory")
        val INTO_COMPONENT = ClassName(ANDROID_ANNOTATION_PACKAGE_NAME, "IntoComponent")
        val FRAGMENT_ENTRY = (ClassName("me.tatarka.inject.android.fragment", "FragmentEntry"))
    }
}