package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import me.tatarka.inject.annotations.*
import me.tatarka.inject.compiler.ast.*
import java.io.File
import java.util.*
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.NoType
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

        val file =
            FileSpec.builder(astClass.element.enclosingElement.toString(), "Inject${astClass.element.simpleName}")
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
                                types.asElement(type.type).simpleName.asScopedProp(),
                                type.asTypeName()
                            ).delegate(codeBlock.build())
                                .build()
                        )
                    }

                    astClass.visitInheritanceChain { parentClass ->
                        for (method in parentClass.methods) {
                            if (method.element.isProvider()) {
                                val codeBlock = CodeBlock.builder()
                                codeBlock.add("return ")
                                val returnType = method.returnTypeFor(astClass)

                                codeBlock.add(provide(TypeKey(returnType), context))

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
        typesWithScope: MutableMap<AstType, TypeElement> = mutableMapOf()
    ): Context {

        val providesMap = mutableMapOf<TypeKey, AstMethod>()
        val providesContainer = mutableMapOf<TypeKey, Pair<String, MutableList<AstMethod>>>()

        val scoped = mutableMapOf<AstType, TypeElement>()

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
            inject.toAstClass()?.let {
                scoped[it.type] = astClass.element
            }
        }

        fun addScope(type: AstType, scope: TypeElement?) {
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
                        val typeArgs = method.returnType.arguments
                        val mapType = declaredTypeOf(Map::class, typeArgs[0], typeArgs[1])

                        providesContainer.computeIfAbsent(TypeKey(mapType)) {
                            "mapOf" to mutableListOf()
                        }.second.add(method)
                    } else if (method.element.getAnnotation(IntoSet::class.java) != null) {
                        // A -> Set<A>
                        val setType = declaredTypeOf(Set::class, method.returnType)

                        providesContainer.computeIfAbsent(TypeKey(setType)) {
                            "setOf" to mutableListOf()
                        }.second.add(method)
                    } else {
                        providesMap[TypeKey(method.returnType)] = method
                        addScope(method.returnType, method.element.scopeType())
                    }
                }
                if (method.element.isProvider()) {
                    val returnType = types.asElement(method.element.returnType)
                    addScope(method.returnType, returnType.scopeType())
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
                "Cannot find an @Inject constructor or @Provides for class: $key on ${context.source}",
                context.source
            )
            throw FailedToGenerateException()
        }
        return when (result) {
            is Result.Provides -> provideProvides(result, context)
            is Result.Scoped -> provideScoped(key, result)
            is Result.Constructor -> provideConstructor(result.constructor, context)
            is Result.Container -> provideContainer(result, context)
            is Result.Function -> provideFunction(result, context)
            is Result.Arg -> provideArg(result)
        }
    }

    private fun provideProvides(
        providesResult: Result.Provides,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        if (providesResult.name != null) {
            codeBlock.add("%L.", providesResult.name)
        }
        val method = providesResult.method

        val receiverParamType = method.receiverParameterType

        when (method) {
            is AstProperty -> {
                if (receiverParamType != null) {
                    codeBlock.add(provide(TypeKey(receiverParamType), context))
                    codeBlock.add(".")
                }
                codeBlock.add("%N", method.name)
            }
            is AstFunction -> {
                if (receiverParamType != null) {
                    codeBlock.add(provide(TypeKey(receiverParamType), context))
                    codeBlock.add(".")
                }
                codeBlock.add("%N(", method.name)
                method.parameters.forEachIndexed { i, param ->
                    if (i != 0) {
                        codeBlock.add(",")
                    }
                    codeBlock.add(provide(TypeKey(param.type), context))
                }
                codeBlock.add(")")
            }
        }
        return codeBlock.build()
    }

    private fun provideConstructor(
        constructor: AstConstructor,
        context: Context
    ): CodeBlock {
        val codeBlock = CodeBlock.builder()
        codeBlock.add("%T(", constructor.type.asTypeName())
        constructor.parameters.forEachIndexed { i, param ->
            if (i != 0) {
                codeBlock.add(",")
            }
            codeBlock.add(provide(TypeKey(param.type), context))
        }
        codeBlock.add(")")
        return codeBlock.build()
    }

    private fun provideScoped(key: TypeKey, result: Result.Scoped): CodeBlock {
        val codeBlock = CodeBlock.builder()
        if (result.name != null) {
            codeBlock.add("(%L as Inject%N).", result.name, result.element.simpleName)
        }
        codeBlock.add("%N", types.asElement(key.type.type).simpleName.asScopedProp())
        return codeBlock.build()
    }

    private fun provideContainer(containerResult: Result.Container, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            add("${containerResult.creator}(")
            containerResult.methods.forEachIndexed { index, method ->
                if (index != 0) {
                    add(", ")
                }
                add(provideProvides(Result.Provides(null, method), context))
            }
            add(")")
        }.build()
    }

    private fun provideFunction(result: Result.Function, context: Context): CodeBlock {
        return CodeBlock.builder().apply {
            beginControlFlow("{")
            val argMap = mutableMapOf<AstType, String>()
            result.args.forEachIndexed { index, arg ->
                if (index != 0) {
                    add(",")
                }
                val name = "arg$index"
                argMap[arg] = name
                add(" %L", name)
            }
            if (result.args.isNotEmpty()) {
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

    private fun Element.getCompanion(): TypeElement? = ElementFilter.typesIn(enclosedElements).firstOrNull()

    private fun Element.constructor(): ExecutableElement? = ElementFilter.constructorsIn(enclosedElements).firstOrNull()

    private fun AstType.qualifier(): List<AstAnnotation> {
        return annotations.filter {
            it.annotationType.asElement().getAnnotation(Qualifier::class.java) != null
        }
    }

    private fun Context.find(key: TypeKey): Result? {
        for ((arg, name) in args) {
            if (arg == key.type) {
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
        if (key.type.toString().startsWith("kotlin.Function0")) {
            // Function0<T> -> T
            val fKey = TypeKey(key.type.arguments[0])
            return Result.Function(key = fKey, args = emptyList())
        } else if (key.type.toString().startsWith("kotlin.Function1")) {
            // Function1<A, B> -> B
            val fKey = TypeKey(key.type.arguments[1])
            return Result.Function(
                key = fKey,
                args = listOf(key.type.arguments[0])
            )
        }
        for (p in parents) {
            val parentResult = p.find(key)
            if (parentResult != null) {
                return parentResult
            }
        }
        val element = types.asElement(key.type.type) as TypeElement
        if (element.getAnnotation(Inject::class.java) != null) {
            val astClass = element.toAstClass() ?: return null
            return Result.Constructor(astClass.constructors[0])
        }
        return null
    }

    data class Context(
        val source: Element? = null,
        val name: String? = null,
        val parents: List<Context>,
        val provides: Map<TypeKey, AstMethod>,
        val providesContainer: Map<TypeKey, Pair<String, List<AstMethod>>>,
        val scoped: Map<AstType, TypeElement> = emptyMap(),
        val args: Map<AstType, String> = emptyMap()
    ) {
        fun withoutScoped() = copy(scoped = emptyMap())

        fun withArgs(args: Map<AstType, String>) = copy(args = args)
    }

    sealed class Result(val name: String?) {
        class Provides(name: String?, val method: AstMethod) : Result(name)
        class Scoped(name: String?, val element: TypeElement) : Result(name)
        class Constructor(val constructor: AstConstructor) : Result(null)
        class Container(val creator: String, val methods: List<AstMethod>) : Result(null)
        class Function(val key: TypeKey, val args: List<AstType>) : Result(null)
        class Arg(val argName: String) : Result(null)
    }

    inner class TypeKey(val type: AstType) {
        val qualifier = type.qualifier()

        override fun equals(other: Any?): Boolean {
            if (other !is TypeKey) return false
            return qualifier == other.qualifier && type.asTypeName() == other.type.asTypeName()
        }

        override fun hashCode(): Int {
            return Objects.hash(type.asTypeName(), qualifier)
        }

        override fun toString(): String {
            return if (qualifier.isNotEmpty()) "${qualifier.joinToString(" ")} $type" else type.toString()
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
