package me.tatarka.inject.compiler

import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.compiler.ContainerCreator.mapOf
import me.tatarka.inject.compiler.ContainerCreator.setOf

class TypeCollector private constructor(private val provider: AstProvider, private val options: Options) :
    AstProvider by provider {

    companion object {
        operator fun invoke(
            provider: AstProvider,
            options: Options,
            astClass: AstClass,
            accessor: String? = null,
            isComponent: Boolean = true,
            scopedInjects: Collection<AstClass> = emptyList()
        ): TypeCollector = TypeCollector(provider, options).apply {
            collectTypes(astClass, accessor, isComponent, scopedInjects)
        }
    }

    // Map of types to inject and how to obtain them
    private val types = mutableMapOf<TypeKey, TypeCreator>()

    // Map of types that can be provided abstract methods
    private val providerMethods = mutableMapOf<TypeKey, TypeCreator>()

    // List of scoped types this component needs to provide
    private val _scoped = mutableListOf<AstType>()

    // Map of scoped components and the accessors to obtain them
    private val scopedAccessors = mutableMapOf<AstClass, ScopedComponent>()

    private fun collectTypes(
        astClass: AstClass,
        accessor: String? = null,
        isComponent: Boolean = true,
        scopedInjects: Collection<AstClass> = emptyList()
    ) {
        val concreteMethods = mutableSetOf<AstMethod>()
        val providesMethods = mutableListOf<AstMethod>()
        val providerMethods = mutableListOf<AstMethod>()

        val elementScopeClass = astClass.scopeClass(messenger, options)
        val elementScope = elementScopeClass?.scopeType(options)

        if (elementScope != null) {
            scopedAccessors[elementScope] = ScopedComponent(astClass, accessor)
        }

        for (injectClass in scopedInjects) {
            if (injectClass.scopeType(options) == elementScope) {
                _scoped.add(injectClass.type)
            }
        }

        astClass.visitInheritanceChain { parentClass ->
            for (method in parentClass.methods) {
                if (isComponent && AstModifier.ABSTRACT !in method.modifiers) {
                    concreteMethods.add(method)
                }
                if (method.isProvides()) {
                    if (AstModifier.PRIVATE in method.modifiers) {
                        error("@Provides method must not be private", method)
                        continue
                    }
                    if (method.returnType.isUnit()) {
                        error("@Provides method must return a value", method)
                        continue
                    }
                    if (isComponent && AstModifier.ABSTRACT in method.modifiers) {
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

        for (method in providesMethods) {
            val scopeType = method.scopeType(options)
            if (scopeType != null && scopeType != elementScope) {
                error("@Provides scope:${scopeType} must match component scope: $elementScope", method)
            }
            val scopedComponent = if (scopeType != null) astClass else null
            if (method.hasAnnotation<IntoMap>()) {
                // Pair<A, B> -> Map<A, B>
                val type = method.returnTypeFor(astClass)
                if (type.packageName == "kotlin" && type.simpleName == "Pair") {
                    val typeArgs = method.returnTypeFor(astClass).arguments
                    val mapType = TypeKey(declaredTypeOf(Map::class, typeArgs[0], typeArgs[1]))
                    addContainerType(mapType, mapOf, method, accessor, scopedComponent)
                } else {
                    error("@IntoMap must have return type of type Pair", method)
                }
            } else if (method.hasAnnotation<IntoSet>()) {
                // A -> Set<A>
                val setType = TypeKey(declaredTypeOf(Set::class, method.returnTypeFor(astClass)))
                addContainerType(setType, setOf, method, accessor, scopedComponent)
            } else {
                val returnType = method.returnTypeFor(astClass)
                val key = TypeKey(returnType)
                addMethod(key, method, accessor, scopedComponent)
                if (scopeType != null) {
                    _scoped.add(returnType)
                }
            }
        }

        for (method in providerMethods) {
            val returnType = method.returnTypeFor(astClass)
            val key = TypeKey(returnType)
            if (isComponent) {
                addProviderMethod(key, method, accessor)
            } else {
                // If we aren't a component ourselves, allow obtaining types from providers as these may be contributed from the
                // eventual implementation
                addMethod(key, method, accessor, scopedComponent = null)
            }
        }

        val constructor = astClass.primaryConstructor
        if (constructor != null) {
            for (parameter in constructor.parameters) {
                if (parameter.isComponent()) {
                    val elemAstClass = parameter.type.toAstClass()
                    collectTypes(
                        elemAstClass,
                        accessor = if (accessor != null) "$accessor.${parameter.name}" else parameter.name,
                        isComponent = elemAstClass.isComponent()
                    )
                }
            }
        }
    }

    private fun addContainerType(
        key: TypeKey,
        creator: ContainerCreator,
        method: AstMethod,
        accessor: String?,
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

    private fun addMethod(key: TypeKey, method: AstMethod, accessor: String?, scopedComponent: AstClass?) {
        val oldValue = types[key]
        if (oldValue == null) {
            types[key] = method(method, accessor, scopedComponent)
        } else {
            duplicate(key, newValue = method, oldValue = oldValue.source)
        }
    }

    private fun addProviderMethod(key: TypeKey, method: AstMethod, accessor: String?) {
        if (!providerMethods.containsKey(key)) {
            providerMethods[key] = method(method, accessor, scopedComponent = null)
        }
    }

    private fun method(method: AstMethod, accessor: String?, scopedComponent: AstClass?) = TypeCreator.Method(
        method = method,
        accessor = accessor,
        scope = method.scopeType(options),
        scopedComponent = scopedComponent
    )

    private fun duplicate(key: TypeKey, newValue: AstElement, oldValue: AstElement) {
        error("Cannot provide: $key", newValue)
        error("as it is already provided", oldValue)
    }

    val scoped: Iterable<AstType> = _scoped

    fun resolve(key: TypeKey, skipSelf: Boolean = false): TypeCreator? {
        if (!skipSelf) {
            val result = providerMethods[key]
            if (result != null) {
                return result
            }
        }
        val result = types[key]
        if (result != null) {
            return result
        }
        val astClass = key.type.toAstClass()
        if (astClass.isInject(options)) {
            val scope = astClass.scopeType(options)
            val scopedComponent = if (scope != null) scopedAccessors[scope] else null
            if (scope != null && scopedComponent == null) {
                error("Cannot find component with scope: @$scope to inject $astClass", astClass)
                return null
            }
            return TypeCreator.Constructor(
                astClass.primaryConstructor!!,
                accessor = scopedComponent?.accessor,
                scope = scope,
                scopedComponent = scopedComponent?.type
            )
        }
        return null
    }
}

sealed class TypeCreator(val source: AstElement) {

    class Constructor(
        val constructor: AstConstructor,
        val accessor: String? = null,
        val scope: AstClass? = null,
        val scopedComponent: AstClass? = null
    ) :
        TypeCreator(constructor)

    class Method(
        val method: AstMethod,
        val accessor: String? = null,
        val scope: AstClass? = null,
        val scopedComponent: AstClass? = null
    ) : TypeCreator(method)

    class Container(val creator: ContainerCreator, source: AstElement, val args: MutableList<Method>) :
        TypeCreator(source)
}

enum class ContainerCreator {
    mapOf, setOf
}

data class ScopedComponent(
    val type: AstClass,
    val accessor: String?
)
