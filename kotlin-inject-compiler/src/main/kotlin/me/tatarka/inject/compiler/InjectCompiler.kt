package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import me.tatarka.inject.annotations.*
import me.tatarka.inject.compiler.ast.AstClass
import me.tatarka.inject.compiler.ast.AstProvider
import me.tatarka.inject.compiler.ast.AstType
import java.io.File
import java.util.*
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.NoType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.reflect.KClass

private const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

class InjectCompiler : AbstractProcessor(), AstProvider {

    private lateinit var generatedSourcesRoot: String
    private lateinit var filer: Filer
    override lateinit var types: Types
    override lateinit var elements: Elements
    override lateinit var messager: Messager

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        this.generatedSourcesRoot = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]!!
        this.filer = processingEnv.filer
        this.types = processingEnv.typeUtils
        this.elements = processingEnv.elementUtils
        this.messager = processingEnv.messager
    }

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        for (element in env.getElementsAnnotatedWith(Module::class.java)) {
            if (element !is TypeElement) continue
            val astClass = element.toAstClass() ?: continue

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
                process(astClass, scope, scopedInjects)
            } catch (e: FailedToGenerateException) {
                // Continue so we can see all errors
                continue
            }
        }
        return false
    }

    private fun process(astClass: AstClass, scope: TypeElement?, scopedInjects: Collection<TypeElement>) {
        val constructor = astClass.element.constructor()
        val companion = astClass.element.getCompanion()

        val injectModule = generateInjectModule(astClass, constructor, scopedInjects)
        val createFunction = generateCreate(astClass.element, constructor, companion, injectModule)

        val file = FileSpec.builder(astClass.element.enclosingElement.toString(), "Inject${astClass.element.simpleName}")
            .addType(injectModule)
            .addFunction(createFunction)
            .build()

        val out = File(generatedSourcesRoot).also { it.mkdir() }

        file.writeTo(out)
    }

    private fun generateInjectModule(
        astClass: AstClass,
        constructor: ExecutableElement?,
        scopedInjects: Collection<TypeElement>
    ): TypeSpec {
        val context = collectTypes(astClass, scopedInjects)

        return TypeSpec.classBuilder("Inject${astClass.element.simpleName}")
            .superclass(astClass.element.asClassName())
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

                try {
                    for ((type, _) in context.scoped) {
                        val codeBlock = CodeBlock.builder()
                        codeBlock.add("lazy { ")
                        codeBlock.add(provide(TypeKey(type), context.withoutScoped()))
                        codeBlock.add(" }")

                        addProperty(
                            PropertySpec.builder(
                                types.asElement(type).simpleName.asScopedProp(),
                                type.asTypeName()
                            ).delegate(codeBlock.build())
                                .build()
                        )
                    }

                    astClass.visitInheritanceChain { parentClass ->
                        for (method in parentClass.methods) {
                            if (method.element.isProvider()) {
                                val qualifier = method.element.qualifier()

                                val codeBlock = CodeBlock.builder()
                                codeBlock.add("return ")
                                val returnType = method.returnTypeFor(astClass)

                                codeBlock.add(provide(TypeKey(returnType.type, qualifier), context))

                                addProperty(
                                    PropertySpec.builder(
                                        method.name,
                                        returnType.asTypeName(),
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
                    }
                } catch (e: FailedToGenerateException) {
                    // Create a stub module to prevent extra compile errors, the original one will still be reported.
                }
            }
            .build()
    }

    private fun generateCreate(
        element: TypeElement,
        constructor: ExecutableElement?,
        companion: TypeElement?,
        injectModule: TypeSpec
    ): FunSpec {
        return FunSpec.builder("create")
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
    }

    private fun collectTypes(
        astClass: AstClass,
        scopedInjects: Collection<TypeElement>,
        name: String? = null,
        typesWithScope: MutableMap<TypeMirror, TypeElement> = mutableMapOf()
    ): Context {

        val providesMap = mutableMapOf<TypeKey, ExecutableElement>()
        val providesContainer = mutableMapOf<TypeKey, Pair<String, MutableList<ExecutableElement>>>()

        val scoped = mutableMapOf<TypeMirror, TypeElement>()

        val elementScope = astClass.element.scopeType()

        val itr = typesWithScope.iterator()
        while (itr.hasNext()) {
            val (type, typeScope) = itr.next()
            if (elementScope == typeScope) {
                scoped[type] = astClass.element
                itr.remove()
            }
        }

        for (inject in scopedInjects) {
            scoped[inject.asType()] = astClass.element
        }

        fun addScope(type: TypeMirror, scope: TypeElement?) {
            if (scope != null) {
                if (scope == elementScope) {
                    scoped[type] = astClass.element
                } else {
                    typesWithScope[type] = scope
                }
            }
        }

        astClass.visitInheritanceChain { parentClass ->
            for (method in parentClass.methods) {
                if (method.element.isProvides()) {
                    if (method.element.getAnnotation(IntoMap::class.java) != null) {
                        // Pair<A, B> -> Map<A, B>
                        val declaredType = method.returnType.type as DeclaredType
                        val mapType = types.getDeclaredType(
                            elements.getTypeElement(Map::class.java.canonicalName),
                            declaredType.typeArguments[0], declaredType.typeArguments[1]
                        )

                        providesContainer.computeIfAbsent(TypeKey(mapType)) {
                            "mapOf" to mutableListOf()
                        }.second.add(method.element)
                    } else if (method.element.getAnnotation(IntoSet::class.java) != null) {
                        // A -> Set<A>
                        val setType = types.getDeclaredType(
                            elements.getTypeElement(Set::class.java.canonicalName),
                            method.returnType.type
                        )

                        providesContainer.computeIfAbsent(TypeKey((setType))) {
                            "setOf" to mutableListOf()
                        }.second.add(method.element)
                    } else {
                        providesMap[TypeKey(method.element.returnType, method.element.qualifier())] = method.element
                        addScope(method.element.returnType, method.element.scopeType())
                    }
                }
                if (method.element.isProvider()) {
                    val returnType = types.asElement(method.element.returnType)
                    addScope(method.element.returnType, returnType.scopeType())
                }
            }
        }

        val parents = mutableListOf<Context>()

        val constructor = astClass.element.constructor()
        if (constructor != null) {
            for (parameter in constructor.parameters) {
                val elem = types.asElement(parameter.asType())
                if (elem.isModule()) {
                    val elemAstClass = (elem as TypeElement).toAstClass() ?: continue
                    parents.add(
                        collectTypes(
                            elemAstClass,
                            scopedInjects,
                            name = parameter.simpleName.toString(),
                            typesWithScope = typesWithScope
                        )
                    )
                }
            }
        }
        return Context(
            source = astClass.element,
            name = name,
            parents = parents,
            provides = providesMap,
            providesContainer = providesContainer,
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
                "Cannot find an @Inject constructor or @Provides for class: ${key.type} on ${context.source}",
                context.source
            )
            throw FailedToGenerateException()
        }
        return when (result) {
            is Result.Provides -> provideProvides(result, context)
            is Result.Scoped -> provideScoped(key, result)
            is Result.Constructor -> provideConstructor(result.element, context)
            is Result.Container -> provideContainer(result, context)
            is Result.Function -> provideFunction(result, context)
            is Result.Arg -> provideArg(result)
        }
    }

    private fun provideProvides(
        providesElement: Result.Provides,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        if (providesElement.name != null) {
            codeBlock.add("%L.", providesElement.name)
        }
        val element = providesElement.element
        val isExtension = element.isExtension()
        if (element.isProp()) {
            if (isExtension) {
                codeBlock.add(provide(TypeKey(element.parameters[0].asType()), context))
                codeBlock.add(".")
            }
            codeBlock.add("%N", element.simpleName.asProp())
        } else {
            if (isExtension) {
                codeBlock.add(provide(TypeKey(element.parameters[0].asType()), context))
                codeBlock.add(".")
            }
            codeBlock.add("%N(", element.simpleName)
            element.parameters.drop(if (isExtension) 1 else 0).forEachIndexed { i, param ->
                if (i != 0) {
                    codeBlock.add(",")
                }
                codeBlock.add(provide(TypeKey(param.asType()), context))
            }
            codeBlock.add(")")
        }
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

    private fun provideScoped(key: TypeKey, result: Result.Scoped): CodeBlock {
        val codeBlock = CodeBlock.builder()
        if (result.name != null) {
            codeBlock.add("(%L as Inject%N).", result.name, result.element.simpleName)
        }
        codeBlock.add("%N", types.asElement(key.type).simpleName.asScopedProp())
        return codeBlock.build()
    }

    private fun provideContainer(providesElement: Result.Container, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            add("${providesElement.creator}(")
            providesElement.elements.forEachIndexed { index, element ->
                if (index != 0) {
                    add(", ")
                }
                add(provideProvides(Result.Provides(null, element), context))
            }
            add(")")
        }.build()
    }

    private fun provideFunction(result: Result.Function, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("{")
            val argMap = mutableMapOf<TypeMirror, String>()
            result.args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(",")
                }
                val name = "arg$index"
                argMap[arg] = name
                add(" %L", name)
            }
            if (!result.args.isEmpty()) {
                add(" ->")
            }

            add(provide(result.key, context.withArgs(argMap)))
            endControlFlow()
        }.build()
    }

    private fun provideArg(result: Result.Arg): CodeBlock {
        return CodeBlock.of(result.argName)
    }

    private fun Name.asProp(): String = toString().removePrefix("get").decapitalize()


    private fun Element.isModule() = getAnnotation(Module::class.java) != null

    private fun ExecutableElement.isProvides(): Boolean =
        !modifiers.contains(Modifier.PRIVATE) && !modifiers.contains(Modifier.ABSTRACT) && returnType !is NoType

    private fun ExecutableElement.isProvider(): Boolean =
        modifiers.contains(Modifier.ABSTRACT) && parameters.isEmpty() && returnType !is NoType

    private fun ExecutableElement.isProp(): Boolean =
        simpleName.startsWith("get") && ((isExtension() && parameters.size == 1) || parameters.isEmpty())

    private fun Element.getCompanion(): TypeElement? = ElementFilter.typesIn(enclosedElements).firstOrNull()

    private fun Element.constructor(): ExecutableElement? = ElementFilter.constructorsIn(enclosedElements).firstOrNull()

    private fun Element.qualifier(): Any? {
        return annotationMirrors.find {
            it.annotationType.asElement().getAnnotation(Qualifier::class.java) != null
        }?.wrap()
    }

    private fun AstType.qualifier(): Any? {
        return annotations.filter { it.annotationMirror.annotationType.asElement().getAnnotation(Qualifier::class.java) != null }
    }

    private fun Context.find(key: TypeKey): Result? {
        for ((arg, name) in args) {
            if (types.isSameType(arg, key.type)) {
                return@find Result.Arg(name)
            }
        }

        provides[key]?.let { result ->
            return@find Result.Provides(name, result)
        }
        providesContainer[key]?.let { (creator, elements) ->
            return@find Result.Container(creator, elements)
        }
        scoped[key.type]?.let { result ->
            return@find Result.Scoped(name, result)
        }
        if (key.type.toString().startsWith("kotlin.jvm.functions.Function0")) {
            // Function0<T> -> T
            val declaredType = key.type as DeclaredType
            val fKey = TypeKey(declaredType.typeArguments[0])
            return Result.Function(key = fKey, args = emptyList())
        } else if (key.type.toString().startsWith("kotlin.jvm.functions.Function1")) {
            // Function1<A, B> -> B
            val declaredType = key.type as DeclaredType
            val fKey = TypeKey(declaredType.typeArguments[1])
            return Result.Function(key = fKey, args = listOf(declaredType.typeArguments[0]))
        }
        for (parent in parents) {
            val parentResult = parent.find(key)
            if (parentResult != null) {
                return parentResult
            }
        }
        val element = types.asElement(key.type) as TypeElement
        if (element.getAnnotation(Inject::class.java) != null) {
            return Result.Constructor(element)
        }
        return null
    }

    data class Context(
        val source: Element? = null,
        val name: String? = null,
        val parents: List<Context>,
        val provides: Map<TypeKey, ExecutableElement>,
        val providesContainer: Map<TypeKey, Pair<String, List<ExecutableElement>>>,
        val scoped: Map<TypeMirror, TypeElement> = emptyMap(),
        val args: Map<TypeMirror, String> = emptyMap()
    ) {
        fun withoutScoped() = copy(scoped = emptyMap())

        fun withArgs(args: Map<TypeMirror, String>) = copy(args = args)
    }

    sealed class Result(val name: String?) {
        class Provides(name: String?, val element: ExecutableElement) : Result(name)
        class Scoped(name: String?, val element: TypeElement) : Result(name)
        class Constructor(val element: TypeElement) : Result(null)
        class Container(val creator: String, val elements: List<ExecutableElement>) : Result(null)
        class Function(val key: TypeKey, val args: List<TypeMirror>): Result(null)
        class Arg(val argName: String): Result(null)
    }
    
    fun TypeKey(type: AstType): TypeKey = TypeKey(type.type, type.qualifier())

    data class TypeKey(val type: TypeMirror, val qualifier: Any? = null) {
        override fun equals(other: Any?): Boolean {
            if (other !is TypeKey) return false
            return qualifier == other.qualifier && type.asTypeName() == other.type.asTypeName()
        }

        override fun hashCode(): Int {
            return Objects.hash(type.asTypeName(), qualifier)
        }
    }

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
