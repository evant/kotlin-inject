package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import me.tatarka.inject.annotations.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.reflect.KClass

private const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

class InjectCompiler : AbstractProcessor() {

    private lateinit var generatedSourcesRoot: String
    private lateinit var filer: Filer
    private lateinit var typeUtils: Types
    private lateinit var messager: Messager

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        this.generatedSourcesRoot = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]!!
        this.filer = processingEnv.filer
        this.typeUtils = processingEnv.typeUtils
        this.messager = processingEnv.messager
    }

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        for (element in env.getElementsAnnotatedWith(Module::class.java)) {
            val scope = element.scopeType()
            val scopedInjects =
                if (scope != null) env.getElementsAnnotatedWith(scope).mapNotNull {
                    // skip module itself, we only want @Inject's annotated with the scope
                    if (it.getAnnotation(Module::class.java) != null) {
                        null
                    } else {
                        it as? TypeElement
                    }
                } else emptyList()
            try {
                process(element as TypeElement, scope, scopedInjects)
            } catch (e: FailedToGenerateException) {
                // Continue so we can see all errors
                continue
            }
        }
        return false
    }

    private fun process(element: TypeElement, scope: TypeElement?, scopedInjects: Collection<TypeElement>) {

        val context = collectTypes(element, scopedInjects)

        val props = mutableListOf<PropertySpec>()

        for (method in context.binds.values) {
            props.add(
                PropertySpec.builder(
                    method.simpleName.asProp(),
                    method.returnType.asTypeName(),
                    KModifier.OVERRIDE
                ).receiver(method.parameters[0].asType().asTypeName())
                    .getter(FunSpec.getterBuilder().addCode("return this").build())
                    .build()
            )
        }

        for ((type, _) in context.scoped) {
            val codeBlock = CodeBlock.builder()
            codeBlock.add("lazy { ")
            codeBlock.add(provide(TypeKey(type), context.withoutScoped()))
            codeBlock.add(" }")
            props.add(
                PropertySpec.builder(
                    typeUtils.asElement(type).simpleName.asScopedProp(),
                    type.asTypeName()
                ).delegate(codeBlock.build())
                    .build()
            )
        }

        for (method in ElementFilter.methodsIn(element.enclosedElements)) {
            if (method.isProvider()) {
                val qualifier = method.qualifier()

                val codeBlock = CodeBlock.builder()
                codeBlock.add("return ")
                codeBlock.add(provide(TypeKey(method.returnType, qualifier), context))
                props.add(
                    PropertySpec.builder(
                        method.simpleName.asProp(),
                        method.returnType.asTypeName(),
                        KModifier.OVERRIDE
                    )
                        .getter(
                            FunSpec.getterBuilder()
                                .addCode(codeBlock.build())
                                .build()
                        )
                        .build()
                )
            }
        }

        val constructor = element.constructor()

        val injectModule = TypeSpec.classBuilder("Inject${element.simpleName}")
            .superclass(element.asClassName())
            .apply {
                if (constructor != null) {
                    val funSpec = FunSpec.constructorBuilder()
                    for (parameter in constructor.parameters) {
                        val p = ParameterSpec.get(parameter)
                        funSpec.addParameter(p)
                        addSuperclassConstructorParameter("%N", p)
                    }
                    primaryConstructor(funSpec.build())
                }
            }
            .addProperties(props)
            .build()

        val companion = element.getCompanion()

        val file = FileSpec.builder(element.enclosingElement.toString(), "Inject${element.simpleName}")
            .addType(injectModule)
            .addFunction(
                FunSpec.builder("create")
                    .apply {
                        if (constructor != null) {
                            addParameters(ParameterSpec.parametersOf(constructor))
                        }
                        if (companion != null) {
                            receiver(companion.asType().asTypeName())
                        } else {
                            receiver(KClass::class.asClassName().plusParameter(element.asType().asTypeName()))
                        }
                    }
                    .returns(element.asType().asTypeName())
                    .apply {
                        val codeBlock = CodeBlock.builder()
                        codeBlock.add("return %L.%N(", element.enclosingElement.toString(), injectModule)
                        if (constructor != null) {
                            constructor.parameters.forEachIndexed { i, parameter ->
                                if (i != 0) {
                                    codeBlock.add(", ")
                                }
                                codeBlock.add("%L", parameter.simpleName.toString())
                            }
                        }
                        codeBlock.add(")")
                        addCode(codeBlock.build())
                    }
                    .build()
            )
            .build()

        val out = File(generatedSourcesRoot).also { it.mkdir() }

        file.writeTo(out)
    }

    private fun collectTypes(
        element: TypeElement,
        scopedInjects: Collection<TypeElement>,
        name: String? = null,
        typesWithScope: MutableMap<TypeMirror, TypeElement> = mutableMapOf()
    ): Context {

        val providesMap = mutableMapOf<TypeKey, ExecutableElement>()
        val bindsMap = mutableMapOf<TypeKey, ExecutableElement>()
        val scoped = mutableMapOf<TypeMirror, TypeElement>()

        val elementScope = element.scopeType()

        val itr = typesWithScope.iterator()
        while (itr.hasNext()) {
            val (type, typeScope) = itr.next()
            if (elementScope == typeScope) {
                scoped[type] = element
                itr.remove()
            }
        }

        for (inject in scopedInjects) {
            scoped[inject.asType()] = element
        }

        fun addScope(type: TypeMirror, scope: TypeElement?) {
            if (scope != null) {
                if (scope == elementScope) {
                    scoped[type] = element
                } else {
                    typesWithScope[type] = scope
                }
            }
        }

        for (method in ElementFilter.methodsIn(element.enclosedElements)) {
            if (method.getAnnotation(Provides::class.java) != null) {
                providesMap[TypeKey(method.returnType, method.qualifier())] = method
                addScope(method.returnType, method.scopeType())
            }
            if (method.getAnnotation(Binds::class.java) != null) {
                bindsMap[TypeKey(method.returnType, method.qualifier())] = method
            }
            if (method.isProvider()) {
                val returnType = typeUtils.asElement(method.returnType)
                addScope(method.returnType, returnType.scopeType())
            }
        }

        val parents = mutableListOf<Context>()

        val constructor = element.constructor()
        if (constructor != null) {
            for (parameter in constructor.parameters) {
                val elem = typeUtils.asElement(parameter.asType())
                if (elem.isModule()) {
                    parents.add(
                        collectTypes(
                            elem as TypeElement,
                            scopedInjects,
                            name = parameter.simpleName.toString(),
                            typesWithScope = typesWithScope
                        )
                    )
                }
            }
        }
        return Context(
            source = element,
            name = name,
            parents = parents,
            provides = providesMap,
            binds = bindsMap,
            scoped = scoped
        )
    }

    private fun provide(
        key: TypeKey,
        context: Context
    ): CodeBlock {
        val result = context.find(key)
        if (result == null) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Cannot find an @Inject constructor, @Provides, or @Binds for class: ${key.type} on ${context.source}",
                context.source
            )
            throw FailedToGenerateException()
        }
        return when (result.kind) {
            Kind.PROVIDES -> provideProvides(result, context)
            Kind.BINDS -> provideBinds(result.element as ExecutableElement, context)
            Kind.SCOPED -> provideScoped(key, result)
            Kind.CONSTRUCTOR -> provideConstructor(result.element, context)
        }
    }

    private fun provideProvides(
        providesElement: Result,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        if (providesElement.name != null) {
            codeBlock.add("%L.", providesElement.name)
        }
        if ((providesElement.element as ExecutableElement).isProp()) {
            codeBlock.add("%N", providesElement.element.simpleName.asProp())
        } else {
            codeBlock.add("%N(", providesElement.element.simpleName)
            providesElement.element.parameters.forEachIndexed { i, param ->
                if (i != 0) {
                    codeBlock.add(",")
                }
                codeBlock.add(provide(TypeKey(param.asType()), context))
            }
            codeBlock.add(")")
        }
        return codeBlock.build()
    }

    private fun provideBinds(
        bindsElement: ExecutableElement,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        val concreate = bindsElement.parameters[0]
        codeBlock.add(provide(TypeKey(concreate.asType(), concreate.qualifier()), context))
        codeBlock.add(".%N", bindsElement.simpleName.asProp())
        return codeBlock.build()
    }

    private fun provideConstructor(
        element: Element,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        codeBlock.add("%T(", element.asType().asTypeName())
        val constructor = element.constructor()!!
        constructor.parameters.forEachIndexed { i, param ->
            if (i != 0) {
                codeBlock.add(",")
            }
            codeBlock.add(provide(TypeKey(param.asType(), param.qualifier()), context))
        }
        codeBlock.add(")")
        return codeBlock.build()
    }

    private fun provideScoped(key: TypeKey, result: Result): CodeBlock {
        val codeBlock = CodeBlock.builder()
        if (result.name != null) {
            codeBlock.add("(%L as Inject%N).", result.name, result.element.simpleName)
        }
        codeBlock.add("%N", typeUtils.asElement(key.type).simpleName.asScopedProp())
        return codeBlock.build()
    }

    private fun Name.asProp(): String = toString().removePrefix("get").decapitalize()


    private fun Element.isModule() = getAnnotation(Module::class.java) != null

    private fun ExecutableElement.isProvider(): Boolean = modifiers.contains(Modifier.ABSTRACT) && parameters.isEmpty()

    private fun ExecutableElement.isProp(): Boolean = simpleName.startsWith("get") && parameters.isEmpty()

    private fun Element.getCompanion(): TypeElement? = ElementFilter.typesIn(enclosedElements).firstOrNull()

    private fun Element.constructor(): ExecutableElement? = ElementFilter.constructorsIn(enclosedElements).firstOrNull()

    private fun Element.qualifier(): Any? =
        annotationMirrors.find {
            it.annotationType.asElement().getAnnotation(Qualifier::class.java) != null
        }?.wrap()

    private fun Context.find(key: TypeKey): Result? {
        provides[key]?.let { result ->
            return@find Result(name, Kind.PROVIDES, result)
        }
        binds[key]?.let { result ->
            return@find Result(name, Kind.BINDS, result)
        }
        scoped[key.type]?.let { result ->
            return@find Result(name, Kind.SCOPED, result)
        }
        for (parent in parents) {
            val parentResult = parent.find(key)
            if (parentResult != null) {
                return parentResult
            }
        }
        val element = typeUtils.asElement(key.type)
        if (element.getAnnotation(Inject::class.java) != null) {
            return Result(null, Kind.CONSTRUCTOR, element)
        }
        return null
    }

    data class Context(
        val source: Element? = null,
        val name: String? = null,
        val parents: List<Context>,
        val provides: Map<TypeKey, ExecutableElement>,
        val binds: Map<TypeKey, ExecutableElement>,
        val scoped: Map<TypeMirror, TypeElement> = emptyMap()
    ) {
        fun withoutScoped() = copy(scoped = emptyMap())

        fun findProvides(key: TypeKey): Result? {
            val result = provides[key]
            if (result != null) {
                return Result(name, Kind.PROVIDES, result)
            }
            for (parent in parents) {
                val parentResult = parent.findProvides(key)
                if (parentResult != null) {
                    return parentResult
                }
            }
            return null;
        }
    }

    data class Result(val name: String?, val kind: Kind, val element: Element)

    enum class Kind { PROVIDES, BINDS, SCOPED, CONSTRUCTOR }

    data class TypeKey(val type: TypeMirror, val qualifier: Any? = null)

    private fun AnnotationMirror.wrap(): AnnotationMirrorWrapper =
        AnnotationMirrorWrapper(annotationType.asTypeName(), elementValues.values.toList().map { it.value })

    data class AnnotationMirrorWrapper(val type: TypeName, val values: List<Any>)

    private class FailedToGenerateException : Exception()

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(
        Module::class.java.canonicalName,
        Inject::class.java.canonicalName
    )

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

}
