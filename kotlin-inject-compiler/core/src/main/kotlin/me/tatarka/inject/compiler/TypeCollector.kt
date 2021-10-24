package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.KModifier
import me.tatarka.inject.compiler.ContainerCreator.mapOf
import me.tatarka.inject.compiler.ContainerCreator.setOf

private val TYPE_INFO_CACHE = mutableMapOf<AstClass, TypeInfo>()

class TypeCollector private constructor(private val provider: AstProvider, private val options: Options) :
    AstProvider by provider {

    companion object {
        operator fun invoke(
            provider: AstProvider,
            options: Options,
            astClass: AstClass,
            accessor: Accessor = Accessor.Empty,
        ): TypeCollector = TypeCollector(provider, options).apply {
            val typeInfo = collectTypeInfo(astClass)
            collectTypes(astClass, accessor, typeInfo)
            scopeClass = typeInfo.scopeClass
            providerMethods = typeInfo.providerMethods
        }
    }

    // Map of types to inject and how to obtain them.
    private val types = mutableMapOf<TypeKey, TypeCreator>()

    // Map of types obtained from generated provider methods. This can be used for lookup when the underlying method
    // is not available (ex: because we only see an interface, or it's marked protected).
    private val providerTypes = mutableMapOf<TypeKey, TypeCreator>()

    // Map of scoped components and the accessors to obtain them
    private val scopedAccessors = mutableMapOf<AstType, ScopedComponent>()

    var scopeClass: AstClass? = null
        private set

    var providerMethods: List<AstMethod> = emptyList()
        private set

    @Suppress("ComplexMethod", "NestedBlockDepth")
    private fun collectTypes(
        astClass: AstClass,
        accessor: Accessor,
        typeInfo: TypeInfo,
    ) {
        if (typeInfo.elementScope != null) {
            scopedAccessors[typeInfo.elementScope] = ScopedComponent(astClass, accessor)
        }

        if (accessor.isNotEmpty()) {
            for (method in typeInfo.providerMethods) {
                val returnType = method.returnTypeFor(astClass)
                val key = TypeKey(returnType, method.qualifier(options))
                addProviderMethod(key, method, accessor)
            }
        }

        for (method in typeInfo.providesMethods) {
            val scopeType = method.scopeType(options)
            if (scopeType != null && scopeType != typeInfo.elementScope) {
                error("@Provides scope:$scopeType must match component scope: ${typeInfo.elementScope}", method)
            }
            val scopedComponent = if (scopeType != null) astClass else null
            if (method.hasAnnotation(INTO_MAP.packageName, INTO_MAP.simpleName)) {
                // Pair<A, B> -> Map<A, B>
                val type = method.returnTypeFor(astClass)
                val resolvedType = type.resolvedType()
                if (resolvedType.packageName == "kotlin" && resolvedType.simpleName == "Pair") {
                    val typeArgs = resolvedType.arguments
                    val mapType =
                        TypeKey(declaredTypeOf(Map::class, typeArgs[0], typeArgs[1]), method.qualifier(options))
                    addContainerType(mapType, mapOf, method, accessor, scopedComponent)
                } else {
                    error("@IntoMap must have return type of type Pair", method)
                }
            } else if (method.hasAnnotation(INTO_SET.packageName, INTO_SET.simpleName)) {
                // A -> Set<A>
                val setType = TypeKey(declaredTypeOf(Set::class, method.returnTypeFor(astClass)))
                addContainerType(setType, setOf, method, accessor, scopedComponent)
            } else {
                val returnType = method.returnTypeFor(astClass)
                val key = TypeKey(returnType, method.qualifier(options))
                if (accessor.isNotEmpty()) {
                    // May have already added from a resolvable provider
                    if (providerTypes.containsKey(key)) continue
                    // We out outside the current class, so complain if not accessible
                    if (method.visibility == KModifier.PROTECTED) {
                        error("@Provides method is not accessible", method)
                    }
                }
                addMethod(key, method, accessor, scopedComponent)
            }
        }

        val constructor = astClass.primaryConstructor
        if (constructor != null) {
            for (parameter in constructor.parameters) {
                if (parameter.isComponent()) {
                    val elemAstClass = parameter.type.toAstClass()
                    val elemTypeInfo = collectTypeInfo(elemAstClass)
                    collectTypes(
                        astClass = elemAstClass,
                        accessor = accessor + parameter.name,
                        typeInfo = elemTypeInfo
                    )
                }
            }
        }
    }

    @Suppress("ComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
    private fun collectTypeInfo(astClass: AstClass): TypeInfo {
        return TYPE_INFO_CACHE.getOrPut(astClass) {
            val isComponent = astClass.isComponent()

            val concreteMethods = mutableSetOf<AstMethod>()
            val providesMethods = mutableListOf<AstMethod>()
            val providerMethods = mutableListOf<AstMethod>()

            var scopeClass: AstClass? = null
            var elementScope: AstType? = null

            astClass.visitInheritanceChain { parentClass ->
                val parentScope = parentClass.scopeType(options)
                if (parentScope != null) {
                    if (scopeClass == null) {
                        scopeClass = parentClass
                        elementScope = parentScope
                    } else {
                        messenger.error("Cannot apply scope: $parentScope", parentClass)
                        messenger.error(
                            "as scope: $elementScope is already applied",
                            scopeClass
                        )
                    }
                }

                for (method in parentClass.methods) {
                    val abstract = method.isAbstract
                    if (isComponent && !abstract) {
                        concreteMethods.add(method)
                    }
                    if (method.isProvides()) {
                        if (method.visibility == KModifier.PRIVATE) {
                            error("@Provides method must not be private", method)
                            continue
                        }
                        if (method.returnType.isUnit()) {
                            error("@Provides method must return a value", method)
                            continue
                        }
                        if (method.returnType.isPlatform()) {
                            val name = method.returnType.simpleName
                            error(
                                """@Provides method must not return a platform type
                                |This can happen when you call a platform method and leave off an explicit return type.
                                |You can fix this be explicitly declaring the return type as $name or $name?"""
                                    .trimMargin(), method
                            )
                            continue
                        }

                        if (isComponent && abstract) {
                            val providesImpl = concreteMethods.find { it.overrides(method) }
                            if (providesImpl == null) {
                                error("@Provides method must have a concrete implementation", method)
                                continue
                            }
                            concreteMethods.remove(providesImpl)
                            providesMethods.add(providesImpl)
                        } else {
                            providesMethods.add(method)
                        }
                    }
                    if (method.isProvider()) {
                        providerMethods.add(method)
                    }
                }
            }

            TypeInfo(
                isComponent = isComponent,
                providesMethods = providesMethods,
                providerMethods = providerMethods,
                scopeClass = scopeClass,
                elementScope = elementScope
            )
        }
    }

    private fun addContainerType(
        key: TypeKey,
        creator: ContainerCreator,
        method: AstMethod,
        accessor: Accessor,
        scopedComponent: AstClass?
    ) {
        val current = types[key]
        if (current == null) {
            types[key] = TypeCreator.Container(
                creator = creator,
                source = method,
                args = mutableListOf(method(method, accessor, scopedComponent))
            )
        } else if (current is TypeCreator.Container && current.creator == creator) {
            current.args.add(method(method, accessor, scopedComponent))
        } else {
            duplicate(key, newValue = method, oldValue = current.source)
        }
    }

    private fun addMethod(key: TypeKey, method: AstMethod, accessor: Accessor, scopedComponent: AstClass?) {
        val oldValue = types[key]
        if (oldValue == null) {
            types[key] = method(method, accessor, scopedComponent)
        } else {
            duplicate(key, newValue = method, oldValue = oldValue.source)
        }
    }

    private fun addProviderMethod(key: TypeKey, method: AstMethod, accessor: Accessor) {
        // Skip adding if already provided by child component.
        if (!providerTypes.containsKey(key)) {
            providerTypes[key] = method(method, accessor, scopedComponent = null)
        }
    }

    private fun method(method: AstMethod, accessor: Accessor, scopedComponent: AstClass?) = TypeCreator.Method(
        method = method,
        accessor = accessor,
        scopedComponent = scopedComponent
    )

    private fun duplicate(key: TypeKey, newValue: AstElement, oldValue: AstElement) {
        error("Cannot provide: $key", newValue)
        error("as it is already provided", oldValue)
    }

    fun resolve(key: TypeKey): TypeCreator? {
        val providerResult = providerTypes[key]
        if (providerResult != null) {
            return providerResult
        }
        val result = types[key]
        if (result != null) {
            return result
        }
        val astClass = key.type.toAstClass()
        val injectCtor = astClass.findInjectConstructors(messenger, options)
        if (injectCtor != null) {
            val scope = astClass.scopeType(options)
            val scopedComponent = if (scope != null) scopedAccessors[scope] else null
            if (scope != null && scopedComponent == null) {
                error("Cannot find component with scope: @$scope to inject $astClass", astClass)
                return null
            }
            return TypeCreator.Constructor(
                injectCtor,
                accessor = scopedComponent?.accessor.orEmpty(),
                scopedComponent = scopedComponent?.type
            )
        }
        if (astClass.isInject() && astClass.isObject) {
            return TypeCreator.Object(astClass)
        }
        return null
    }
}

class TypeInfo(
    val isComponent: Boolean,
    val providesMethods: List<AstMethod>,
    val providerMethods: List<AstMethod>,
    val scopeClass: AstClass? = null,
    val elementScope: AstType? = null,
)

sealed class TypeCreator(val source: AstElement) {

    class Object(val astClass: AstClass) : TypeCreator(astClass)

    class Constructor(
        val constructor: AstConstructor,
        val accessor: Accessor = Accessor.Empty,
        val scopedComponent: AstClass? = null
    ) : TypeCreator(constructor)

    class Method(
        val method: AstMethod,
        val accessor: Accessor = Accessor.Empty,
        val scopedComponent: AstClass? = null
    ) : TypeCreator(method)

    class Container(
        val creator: ContainerCreator,
        val args: MutableList<Method>,
        source: AstElement
    ) : TypeCreator(source)
}

@Suppress("EnumNaming")
enum class ContainerCreator {
    mapOf, setOf
}

data class ScopedComponent(
    val type: AstClass,
    val accessor: Accessor
)